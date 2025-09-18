package com.techstore.service;

import com.techstore.dto.ProductResponseDTO;
import com.techstore.dto.request.ProductCreateRequestDTO;
import com.techstore.dto.request.ProductImageOperationsDTO;
import com.techstore.dto.request.ProductImageUpdateDTO;
import com.techstore.dto.request.ProductParameterCreateDTO;
import com.techstore.dto.request.ProductUpdateRequestDTO;
import com.techstore.dto.response.CategorySummaryDTO;
import com.techstore.dto.response.ManufacturerSummaryDto;
import com.techstore.dto.response.ParameterOptionResponseDto;
import com.techstore.dto.response.ProductImageUploadResponseDTO;
import com.techstore.dto.response.ProductParameterResponseDto;
import com.techstore.entity.Category;
import com.techstore.entity.Manufacturer;
import com.techstore.entity.Parameter;
import com.techstore.entity.ParameterOption;
import com.techstore.entity.Product;
import com.techstore.entity.ProductParameter;
import com.techstore.enums.ProductStatus;
import com.techstore.exception.BusinessLogicException;
import com.techstore.exception.DuplicateResourceException;
import com.techstore.exception.ValidationException;
import com.techstore.mapper.ParameterMapper;
import com.techstore.repository.CategoryRepository;
import com.techstore.repository.ManufacturerRepository;
import com.techstore.repository.ParameterOptionRepository;
import com.techstore.repository.ParameterRepository;
import com.techstore.repository.ProductRepository;
import com.techstore.util.ExceptionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final ParameterRepository parameterRepository;
    private final ParameterOptionRepository parameterOptionRepository;
    private final S3Service s3Service;
    private final ParameterMapper parameterMapper;

    // Constants for validation
    private static final int MAX_IMAGES_PER_PRODUCT = 20;
    private static final int MAX_PARAMETERS_PER_PRODUCT = 100;
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    // ============ CREATE OPERATIONS ============

    @Transactional(readOnly = true)
    public void debugProductParameters(Long productId) {
        log.info("=== DEBUG PRODUCT PARAMETERS ===");

        Product product = findProductByIdOrThrow(productId);
        log.info("Product ID: {}, Name: {}", product.getId(), product.getNameEn());

        // Debug product parameters
        Set<ProductParameter> productParameters = product.getProductParameters();
        log.info("ProductParameters Set Size: {}", productParameters.size());

        int counter = 1;
        for (ProductParameter pp : productParameters) {
            log.info("--- Parameter {} ---", counter++);
            log.info("ProductParameter ID: {}", pp.getId());

            if (pp.getParameter() != null) {
                log.info("Parameter ID: {}, External ID: {}, Name EN: {}, Name BG: {}",
                        pp.getParameter().getId(),
                        pp.getParameter().getExternalId(),
                        pp.getParameter().getNameEn(),
                        pp.getParameter().getNameBg());
            } else {
                log.error("Parameter is NULL!");
            }

            if (pp.getParameterOption() != null) {
                log.info("Option ID: {}, External ID: {}, Name EN: {}, Name BG: {}",
                        pp.getParameterOption().getId(),
                        pp.getParameterOption().getExternalId(),
                        pp.getParameterOption().getNameEn(),
                        pp.getParameterOption().getNameBg());
            } else {
                log.error("Parameter Option is NULL!");
            }
        }

        // Test conversion
        log.info("=== TESTING CONVERSION ===");
        List<ProductParameterResponseDto> specifications = productParameters.stream()
                .map(productParameter -> {
                    ProductParameterResponseDto dto = convertToProductParameterResponse(productParameter, "en");
                    log.info("Converted: Parameter ID={}, Name={}, Options Count={}",
                            dto.getParameterId(), dto.getParameterNameEn(), dto.getOptions().size());
                    return dto;
                })
                .toList();

        log.info("Final Specifications List Size: {}", specifications.size());

        // Test the grouping logic from convertToResponseDTO
        log.info("=== TESTING GROUPING LOGIC ===");
        Map<Long, ProductParameterResponseDto> uniqueSpecs = productParameters.stream()
                .filter(pp -> pp.getParameter() != null && pp.getParameterOption() != null)
                .collect(Collectors.toMap(
                        pp -> pp.getParameter().getId(),  // Group by parameter ID
                        pp -> convertToProductParameterResponse(pp, "en"),
                        (existing, replacement) -> {
                            log.info("DUPLICATE FOUND! Parameter ID: {}, keeping existing",
                                    existing.getParameterId());
                            return existing;  // Keep first one
                        }));

        log.info("Unique Specs Map Size: {}", uniqueSpecs.size());
        uniqueSpecs.forEach((id, spec) ->
                log.info("Unique Spec: ID={}, Name={}", id, spec.getParameterNameEn()));
    }

    @CacheEvict(value = "products", allEntries = true)
    public ProductResponseDTO createProductWithImages(
            ProductCreateRequestDTO productData,
            MultipartFile primaryImage,
            List<MultipartFile> additionalImages, String lang) {

        log.info("Creating product with reference: {} and {} images",
                productData.getReferenceNumber(),
                1 + (additionalImages != null ? additionalImages.size() : 0));

        String context = ExceptionHelper.createErrorContext(
                "createProductWithImages", "Product", null,
                "reference: " + productData.getReferenceNumber());

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            // Comprehensive validation
            validateProductCreateRequest(productData, true);
            validatePrimaryImage(primaryImage);
            validateAdditionalImages(additionalImages);

            // Check for duplicates
            checkForDuplicateProduct(productData.getReferenceNumber(), null);

            List<String> uploadedImageUrls = new ArrayList<>();

            try {
                // Upload primary image
                log.debug("Uploading primary image for product: {}", productData.getReferenceNumber());
                String primaryImageUrl = uploadImageSafely(primaryImage, "products");
                uploadedImageUrls.add(primaryImageUrl);

                // Upload additional images
                List<String> additionalImageUrls = uploadAdditionalImages(additionalImages, uploadedImageUrls);

                // Create product
                Product product = createProductFromCreateRequest(productData);
                product.setPrimaryImageUrl(primaryImageUrl);
                if (!additionalImageUrls.isEmpty()) {
                    product.setAdditionalImages(additionalImageUrls);
                }

                product = productRepository.save(product);

                log.info("Product created successfully with id: {} and {} images",
                        product.getId(), uploadedImageUrls.size());

                return convertToResponseDTO(product, lang);

            } catch (Exception e) {
                log.error("Product creation failed, cleaning up uploaded images", e);
                cleanupImagesOnError(uploadedImageUrls);
                throw e;
            }
        }, context);
    }

    @CacheEvict(value = "products", allEntries = true)
    public ProductImageUploadResponseDTO addImageToProduct(Long productId, MultipartFile file, boolean isPrimary) {
        log.info("Adding image to product {} (isPrimary: {})", productId, isPrimary);

        String context = ExceptionHelper.createErrorContext(
                "addImageToProduct", "Product", productId, "isPrimary: " + isPrimary);

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            // Validate inputs
            validateProductId(productId);
            validateImageFile(file);

            // Find product
            Product product = findProductByIdOrThrow(productId);

            // Validate business rules
            validateImageLimits(product);

            // Upload image
            String imageUrl = uploadImageSafely(file, "products");

            try {
                updateProductImagesForAdd(product, imageUrl, isPrimary);
                productRepository.save(product);

                return createImageUploadResponse(file, imageUrl, isPrimary, product);

            } catch (Exception e) {
                cleanupImageOnError(imageUrl);
                throw e;
            }
        }, context);
    }

    // ============ READ OPERATIONS ============

    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "'all_' + #pageable.pageNumber + '_' + #pageable.pageSize + '_' + #lang")
    public Page<ProductResponseDTO> getAllProducts(Pageable pageable, String lang) {
        log.debug("Fetching all products - Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());

        validatePaginationParameters(pageable);
        validateLanguage(lang);

        return ExceptionHelper.wrapDatabaseOperation(() ->
                        productRepository.findByActiveTrue(pageable)
                                .map(p -> convertToResponseDTO(p, lang)),
                "fetch all products"
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "#id + '_' + #lang")
    public ProductResponseDTO getProductById(Long id, String lang) {
        log.debug("Fetching product with id: {}", id);

        validateProductId(id);
        validateLanguage(lang);

        Product product = findProductByIdOrThrow(id);
        return convertToResponseDTO(product, lang);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getProductsByCategory(Long categoryId, Pageable pageable, String lang) {
        log.debug("Fetching products by category: {}", categoryId);

        String context = ExceptionHelper.createErrorContext(
                "getProductsByCategory", "Product", null, "categoryId: " + categoryId);

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            validateCategoryId(categoryId);
            validatePaginationParameters(pageable);
            validateLanguage(lang);

            // Verify category exists
            findCategoryByIdOrThrow(categoryId);

            return productRepository.findByActiveTrueAndCategoryId(categoryId, pageable)
                    .map(p -> convertToResponseDTO(p, lang));
        }, context);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getProductsByBrand(Long brandId, Pageable pageable, String lang) {
        log.debug("Fetching products by brand: {}", brandId);

        String context = ExceptionHelper.createErrorContext(
                "getProductsByBrand", "Product", null, "brandId: " + brandId);

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            validateManufacturerId(brandId);
            validatePaginationParameters(pageable);
            validateLanguage(lang);

            // Verify manufacturer exists
            findManufacturerByIdOrThrow(brandId);

            return productRepository.findByActiveTrueAndManufacturerId(brandId, pageable)
                    .map(p -> convertToResponseDTO(p, lang));
        }, context);
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getRelatedProducts(Long productId, int limit, String lang) {
        log.debug("Fetching related products for product: {}", productId);

        String context = ExceptionHelper.createErrorContext(
                "getRelatedProducts", "Product", productId, "limit: " + limit);

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            validateProductId(productId);
            validateRelatedProductsLimit(limit);
            validateLanguage(lang);

            Product product = findProductByIdOrThrow(productId);

            validateProductForRelated(product);

            Pageable pageable = Pageable.ofSize(limit);
            return productRepository.findRelatedProducts(
                            productId,
                            product.getCategory().getId(),
                            product.getManufacturer().getId(),
                            pageable
                    ).stream()
                    .map(p -> convertToResponseDTO(p, lang))
                    .toList();
        }, context);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> searchProducts(String query, Pageable pageable, String lang) {
        log.debug("Searching products with query: {}", query);

        String context = ExceptionHelper.createErrorContext(
                "searchProducts", "Product", null, "query: " + query);

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            validateSearchQuery(query);
            validatePaginationParameters(pageable);
            validateLanguage(lang);

            return productRepository.searchProducts(query, pageable)
                    .map(p -> convertToResponseDTO(p, lang));
        }, context);
    }

    // ============ UPDATE OPERATIONS ============

    @CacheEvict(value = "products", allEntries = true)
    public ProductResponseDTO updateProductWithImages(
            Long id,
            ProductUpdateRequestDTO productData,
            MultipartFile newPrimaryImage,
            List<MultipartFile> newAdditionalImages,
            ProductImageOperationsDTO imageOperations, String lang) {

        log.info("Updating product with id: {} with image operations", id);

        String context = ExceptionHelper.createErrorContext("updateProductWithImages", "Product", id, null);

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            // Validate inputs
            validateProductId(id);
            validateProductUpdateRequest(productData);
            if (newPrimaryImage != null) validateImageFile(newPrimaryImage);
            validateAdditionalImages(newAdditionalImages);
            validateLanguage(lang);

            // Find product
            Product product = findProductByIdOrThrow(id);

            // Check for reference number conflicts
            checkForDuplicateProduct(productData.getReferenceNumber(), id);

            List<String> newUploadedImages = new ArrayList<>();
            List<String> imagesToCleanup = new ArrayList<>();

            try {
                // Handle image operations
                processImageOperations(product, imageOperations, newPrimaryImage,
                        newAdditionalImages, newUploadedImages, imagesToCleanup);

                // Ensure product has at least one image
                validateProductHasImages(product);

                // Update product fields
                updateProductFieldsFromRest(product, productData);

                product = productRepository.save(product);

                // Cleanup old images
                if (!imagesToCleanup.isEmpty()) {
                    cleanupImagesOnError(imagesToCleanup);
                }

                log.info("Product updated successfully with id: {}", product.getId());
                return convertToResponseDTO(product, lang);

            } catch (Exception e) {
                log.error("Product update failed, cleaning up new uploads", e);
                cleanupImagesOnError(newUploadedImages);
                throw e;
            }
        }, context);
    }

    @CacheEvict(value = "products", allEntries = true)
    public ProductResponseDTO reorderProductImages(Long productId, List<ProductImageUpdateDTO> images, String lang) {
        log.info("Reordering images for product {}", productId);

        String context = ExceptionHelper.createErrorContext("reorderProductImages", "Product", productId, null);

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            validateProductId(productId);
            validateImageReorderRequest(images);
            validateLanguage(lang);

            Product product = findProductByIdOrThrow(productId);

            handleImageReordering(product, images);
            product = productRepository.save(product);

            return convertToResponseDTO(product, lang);
        }, context);
    }

    // ============ DELETE OPERATIONS ============

    @CacheEvict(value = "products", allEntries = true)
    public void deleteProduct(Long id) {
        log.info("Deleting product with id: {}", id);

        String context = ExceptionHelper.createErrorContext("deleteProduct", "Product", id, null);

        ExceptionHelper.wrapDatabaseOperation(() -> {
            validateProductId(id);

            Product product = findProductByIdOrThrow(id);

            // Business validation for deletion
            validateProductDeletion(product);

            // Collect images for cleanup
            List<String> allImages = collectAllProductImages(product);

            // Soft delete
            product.setActive(false);
            productRepository.save(product);

            // Cleanup images
            cleanupImagesOnError(allImages);

            log.info("Product soft deleted successfully with id: {}", id);
            return null;
        }, context);
    }

    @CacheEvict(value = "products", allEntries = true)
    public void permanentDeleteProduct(Long id) {
        log.warn("Permanently deleting product with id: {}", id);

        String context = ExceptionHelper.createErrorContext("permanentDeleteProduct", "Product", id, null);

        ExceptionHelper.wrapDatabaseOperation(() -> {
            validateProductId(id);

            Product product = findProductByIdOrThrow(id);

            // Strict validation for permanent deletion
            validatePermanentProductDeletion(product);

            // Collect images for cleanup
            List<String> allImages = collectAllProductImages(product);

            productRepository.deleteById(id);

            // Cleanup images
            cleanupImagesOnError(allImages);

            log.warn("Product permanently deleted successfully with id: {}", id);
            return null;
        }, context);
    }

    @CacheEvict(value = "products", allEntries = true)
    public void deleteProductImage(Long productId, String imageUrl) {
        log.info("Deleting image {} from product {}", imageUrl, productId);

        String context = ExceptionHelper.createErrorContext(
                "deleteProductImage", "Product", productId, "imageUrl: " + imageUrl);

        ExceptionHelper.wrapDatabaseOperation(() -> {
            validateProductId(productId);
            validateImageUrl(imageUrl);

            Product product = findProductByIdOrThrow(productId);

            boolean wasDeleted = removeImageFromProduct(product, imageUrl);

            if (!wasDeleted) {
                throw new ValidationException("Image not found for this product");
            }

            // Ensure product has at least one image
            ensureProductHasPrimaryImage(product);

            productRepository.save(product);

            // Cleanup the deleted image
            cleanupImageOnError(imageUrl);

            log.info("Deleted image {} from product {}", imageUrl, productId);
            return null;
        }, context);
    }

    // ============ UTILITY METHODS ============

    @Transactional(readOnly = true)
    public String getOriginalImageUrl(Long productId, boolean isPrimary, int index) {
        log.debug("Getting original image URL for product: {} (isPrimary: {}, index: {})",
                productId, isPrimary, index);

        String context = ExceptionHelper.createErrorContext(
                "getOriginalImageUrl", "Product", productId,
                String.format("isPrimary: %s, index: %d", isPrimary, index));

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            validateProductId(productId);
            validateImageIndex(index);

            Product product = findProductByIdOrThrow(productId);

            if (isPrimary) {
                return product.getPrimaryImageUrl();
            } else {
                if (product.getAdditionalImages() != null &&
                        index >= 0 && index < product.getAdditionalImages().size()) {
                    return product.getAdditionalImages().get(index);
                }
            }

            return null;
        }, context);
    }

    // ============ VALIDATION METHODS ============

    private void validateProductId(Long id) {
        if (id == null || id <= 0) {
            throw new ValidationException("Product ID must be a positive number");
        }
    }

    private void validateCategoryId(Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            throw new ValidationException("Category ID must be a positive number");
        }
    }

    private void validateManufacturerId(Long manufacturerId) {
        if (manufacturerId == null || manufacturerId <= 0) {
            throw new ValidationException("Manufacturer ID must be a positive number");
        }
    }

    private void validateLanguage(String language) {
        if (!StringUtils.hasText(language)) {
            throw new ValidationException("Language is required");
        }

        if (!language.matches("^(en|bg)$")) {
            throw new ValidationException("Language must be 'en' or 'bg'");
        }
    }

    private void validatePaginationParameters(Pageable pageable) {
        if (pageable.getPageNumber() < 0) {
            throw new ValidationException("Page number cannot be negative");
        }

        if (pageable.getPageSize() <= 0) {
            throw new ValidationException("Page size must be positive");
        }

        if (pageable.getPageSize() > 100) {
            throw new ValidationException("Page size cannot exceed 100");
        }
    }

    private void validateProductCreateRequest(ProductCreateRequestDTO requestDTO, boolean isCreate) {
        if (requestDTO == null) {
            throw new ValidationException("Product request cannot be null");
        }

        // Validate required fields
        validateReferenceNumber(requestDTO.getReferenceNumber(), isCreate);
        validateProductName(requestDTO.getNameEn(), "EN");
        validateCategoryId(requestDTO.getCategoryId());
        validateManufacturerId(requestDTO.getManufacturerId());
        validateProductStatus(requestDTO.getStatus());

        // Validate optional fields
        validateOptionalProductFields(requestDTO);

        // Validate parameters
        validateProductParameters(requestDTO.getParameters());
    }

    private void validateProductUpdateRequest(ProductUpdateRequestDTO requestDTO) {
        validateProductCreateRequest(requestDTO, false);

        // Additional validation for update-specific fields
        if (requestDTO.getImages() != null) {
            validateImageUpdateList(requestDTO.getImages());
        }

        if (requestDTO.getImagesToDelete() != null) {
            validateImagesToDelete(requestDTO.getImagesToDelete());
        }
    }

    private void validateReferenceNumber(String referenceNumber, boolean isRequired) {
        if (isRequired && !StringUtils.hasText(referenceNumber)) {
            throw new ValidationException("Reference number is required");
        }

        if (StringUtils.hasText(referenceNumber)) {
            if (referenceNumber.trim().length() > 100) {
                throw new ValidationException("Reference number cannot exceed 100 characters");
            }

            if (referenceNumber.trim().length() < 3) {
                throw new ValidationException("Reference number must be at least 3 characters long");
            }
        }
    }

    private void validateProductName(String name, String language) {
        if (!StringUtils.hasText(name)) {
            throw new ValidationException(String.format("Product name (%s) is required", language));
        }

        if (name.trim().length() > 500) {
            throw new ValidationException(
                    String.format("Product name (%s) cannot exceed 500 characters", language));
        }

        if (name.trim().length() < 2) {
            throw new ValidationException(
                    String.format("Product name (%s) must be at least 2 characters long", language));
        }
    }

    private void validateProductStatus(Integer status) {
        if (status == null) {
            throw new ValidationException("Product status is required");
        }

        if (status < 0 || status > 4) {
            throw new ValidationException("Product status must be between 0 and 4");
        }
    }

    private void validateOptionalProductFields(ProductCreateRequestDTO requestDTO) {
        if (StringUtils.hasText(requestDTO.getNameBg()) && requestDTO.getNameBg().length() > 500) {
            throw new ValidationException("Product name (BG) cannot exceed 500 characters");
        }

        if (StringUtils.hasText(requestDTO.getDescriptionEn()) && requestDTO.getDescriptionEn().length() > 2000) {
            throw new ValidationException("Product description (EN) cannot exceed 2000 characters");
        }

        if (StringUtils.hasText(requestDTO.getDescriptionBg()) && requestDTO.getDescriptionBg().length() > 2000) {
            throw new ValidationException("Product description (BG) cannot exceed 2000 characters");
        }

        if (StringUtils.hasText(requestDTO.getModel()) && requestDTO.getModel().length() > 100) {
            throw new ValidationException("Product model cannot exceed 100 characters");
        }

        if (StringUtils.hasText(requestDTO.getBarcode()) && requestDTO.getBarcode().length() > 50) {
            throw new ValidationException("Product barcode cannot exceed 50 characters");
        }

        // Validate numeric fields
        validateProductPrices(requestDTO);
        validateProductMeasurements(requestDTO);
    }

    private void validateProductPrices(ProductCreateRequestDTO requestDTO) {
        if (requestDTO.getPriceClient() != null && requestDTO.getPriceClient().compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Client price cannot be negative");
        }

        if (requestDTO.getPricePartner() != null && requestDTO.getPricePartner().compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Partner price cannot be negative");
        }

        if (requestDTO.getMarkupPercentage() != null) {
            if (requestDTO.getMarkupPercentage().compareTo(BigDecimal.valueOf(-50.0)) < 0) {
                throw new ValidationException("Markup percentage cannot be less than -50%");
            }
            if (requestDTO.getMarkupPercentage().compareTo(BigDecimal.valueOf(200.0)) > 0) {
                throw new ValidationException("Markup percentage cannot exceed 200%");
            }
        }
    }

    private void validateProductMeasurements(ProductCreateRequestDTO requestDTO) {
        if (requestDTO.getWeight() != null && requestDTO.getWeight().compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Product weight cannot be negative");
        }

        if (requestDTO.getWarranty() != null && requestDTO.getWarranty() < 0) {
            throw new ValidationException("Product warranty cannot be negative");
        }
    }

    private void validateProductParameters(List<ProductParameterCreateDTO> parameters) {
        if (parameters == null) {
            return;
        }

        if (parameters.size() > MAX_PARAMETERS_PER_PRODUCT) {
            throw new ValidationException(
                    String.format("Product cannot have more than %d parameters", MAX_PARAMETERS_PER_PRODUCT));
        }

        Set<Long> parameterIds = new HashSet<>();
        Set<String> duplicateCheck = new HashSet<>();

        for (ProductParameterCreateDTO param : parameters) {
            if (param.getParameterId() == null) {
                throw new ValidationException("Parameter ID is required");
            }

            if (param.getParameterOptionId() == null) {
                throw new ValidationException("Parameter option ID is required");
            }

            parameterIds.add(param.getParameterId());

            String key = param.getParameterId() + ":" + param.getParameterOptionId();
            if (duplicateCheck.contains(key)) {
                throw new ValidationException("Duplicate parameter-option combination found");
            }
            duplicateCheck.add(key);
        }
    }

    private void validatePrimaryImage(MultipartFile primaryImage) {
        if (primaryImage == null || primaryImage.isEmpty()) {
            throw new ValidationException("Primary image is required for product creation");
        }

        validateImageFile(primaryImage);
    }

    private void validateAdditionalImages(List<MultipartFile> additionalImages) {
        if (additionalImages == null || additionalImages.isEmpty()) {
            return;
        }

        if (additionalImages.size() > MAX_IMAGES_PER_PRODUCT - 1) {
            throw new ValidationException(
                    String.format("Cannot have more than %d additional images", MAX_IMAGES_PER_PRODUCT - 1));
        }

        for (MultipartFile image : additionalImages) {
            if (image != null && !image.isEmpty()) {
                validateImageFile(image);
            }
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Image file cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ValidationException(
                    String.format("Image file size cannot exceed %d bytes", MAX_FILE_SIZE));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ValidationException("File must be an image");
        }

        // Validate allowed image types
        List<String> allowedTypes = List.of("image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp");
        if (!allowedTypes.contains(contentType.toLowerCase())) {
            throw new ValidationException("Image type not allowed. Allowed types: " + allowedTypes);
        }
    }

    private void validateImageLimits(Product product) {
        int currentImageCount = 1; // Primary image
        if (product.getAdditionalImages() != null) {
            currentImageCount += product.getAdditionalImages().size();
        }

        if (currentImageCount >= MAX_IMAGES_PER_PRODUCT) {
            throw new BusinessLogicException(
                    String.format("Product already has maximum of %d images", MAX_IMAGES_PER_PRODUCT));
        }
    }

    private void validateSearchQuery(String query) {
        if (!StringUtils.hasText(query)) {
            throw new ValidationException("Search query cannot be empty");
        }

        if (query.trim().length() > 200) {
            throw new ValidationException("Search query cannot exceed 200 characters");
        }

        if (query.trim().length() < 2) {
            throw new ValidationException("Search query must be at least 2 characters long");
        }
    }

    private void validateRelatedProductsLimit(int limit) {
        if (limit <= 0) {
            throw new ValidationException("Related products limit must be positive");
        }

        if (limit > 50) {
            throw new ValidationException("Related products limit cannot exceed 50");
        }
    }

    private void validateImageUrl(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            throw new ValidationException("Image URL cannot be empty");
        }
    }

    private void validateImageIndex(int index) {
        if (index < 0) {
            throw new ValidationException("Image index cannot be negative");
        }
    }

    private void validateProductForRelated(Product product) {
        if (product.getCategory() == null) {
            throw new BusinessLogicException("Product must have a category to find related products");
        }

        if (product.getManufacturer() == null) {
            throw new BusinessLogicException("Product must have a manufacturer to find related products");
        }
    }

    private void validateProductDeletion(Product product) {
        // Check if product has dependencies that prevent deletion
        if (product.getFavorites() != null && !product.getFavorites().isEmpty()) {
            log.info("Product {} has {} favorites that will be removed",
                    product.getId(), product.getFavorites().size());
        }

        if (product.getCartItems() != null && !product.getCartItems().isEmpty()) {
            log.info("Product {} has {} cart items that will be removed",
                    product.getId(), product.getCartItems().size());
        }
    }

    private void validatePermanentProductDeletion(Product product) {
        validateProductDeletion(product);

        if (product.getActive()) {
            throw new BusinessLogicException("Product must be deactivated before permanent deletion");
        }
    }

    private void validateImageReorderRequest(List<ProductImageUpdateDTO> images) {
        if (images == null || images.isEmpty()) {
            throw new ValidationException("Image reorder list cannot be empty");
        }

        if (images.size() > MAX_IMAGES_PER_PRODUCT) {
            throw new ValidationException(
                    String.format("Cannot reorder more than %d images", MAX_IMAGES_PER_PRODUCT));
        }

        long primaryCount = images.stream().mapToLong(img -> Boolean.TRUE.equals(img.getIsPrimary()) ? 1 : 0).sum();

        if (primaryCount != 1) {
            throw new ValidationException("Exactly one image must be marked as primary");
        }

        // Check for duplicate URLs
        Set<String> urls = new HashSet<>();
        for (ProductImageUpdateDTO img : images) {
            if (!StringUtils.hasText(img.getImageUrl())) {
                throw new ValidationException("Image URL cannot be empty");
            }

            if (urls.contains(img.getImageUrl())) {
                throw new ValidationException("Duplicate image URL in reorder list: " + img.getImageUrl());
            }
            urls.add(img.getImageUrl());
        }
    }

    private void validateImageUpdateList(List<ProductImageUpdateDTO> images) {
        if (images.isEmpty()) {
            return;
        }

        for (ProductImageUpdateDTO image : images) {
            if (!StringUtils.hasText(image.getImageUrl())) {
                throw new ValidationException("Image URL cannot be empty in update list");
            }
        }
    }

    private void validateImagesToDelete(List<String> imagesToDelete) {
        for (String imageUrl : imagesToDelete) {
            if (!StringUtils.hasText(imageUrl)) {
                throw new ValidationException("Image URL to delete cannot be empty");
            }
        }
    }

    private void validateProductHasImages(Product product) {
        if (product.getPrimaryImageUrl() == null) {
            if (product.getAdditionalImages() == null || product.getAdditionalImages().isEmpty()) {
                throw new BusinessLogicException("Product must have at least one image");
            }
        }
    }

    // ============ HELPER METHODS ============

    private Product findProductByIdOrThrow(Long id) {
        return ExceptionHelper.findOrThrow(
                productRepository.findById(id).orElse(null),
                "Product",
                id
        );
    }

    private Category findCategoryByIdOrThrow(Long categoryId) {
        return ExceptionHelper.findOrThrow(
                categoryRepository.findById(categoryId).orElse(null),
                "Category",
                categoryId
        );
    }

    private Manufacturer findManufacturerByIdOrThrow(Long manufacturerId) {
        return ExceptionHelper.findOrThrow(
                manufacturerRepository.findById(manufacturerId).orElse(null),
                "Manufacturer",
                manufacturerId
        );
    }

    private void checkForDuplicateProduct(String referenceNumber, Long excludeId) {
        if (!StringUtils.hasText(referenceNumber)) {
            return;
        }

        boolean exists = (excludeId == null) ?
                productRepository.existsByReferenceNumberIgnoreCase(referenceNumber) :
                productRepository.findAll().stream()
                        .anyMatch(p -> !p.getId().equals(excludeId) &&
                                p.getReferenceNumber().equalsIgnoreCase(referenceNumber));

        if (exists) {
            throw new DuplicateResourceException(
                    "Product already exists with reference number: " + referenceNumber);
        }
    }

    private String uploadImageSafely(MultipartFile file, String folder) {
        try {
            return s3Service.uploadProductImage(file, folder);
        } catch (Exception e) {
            log.error("Error uploading image: {}", e.getMessage());
            throw new BusinessLogicException("Failed to upload image: " + e.getMessage());
        }
    }

    private List<String> uploadAdditionalImages(List<MultipartFile> additionalImages, List<String> uploadedTracker) {
        List<String> additionalImageUrls = new ArrayList<>();

        if (additionalImages != null && !additionalImages.isEmpty()) {
            log.debug("Uploading {} additional images", additionalImages.size());

            for (MultipartFile additionalImage : additionalImages) {
                if (!additionalImage.isEmpty()) {
                    String additionalImageUrl = uploadImageSafely(additionalImage, "products");
                    additionalImageUrls.add(additionalImageUrl);
                    uploadedTracker.add(additionalImageUrl);
                }
            }
        }

        return additionalImageUrls;
    }

    private void cleanupImagesOnError(List<String> imageUrls) {
        if (imageUrls != null && !imageUrls.isEmpty()) {
            try {
                s3Service.deleteImages(imageUrls);
            } catch (Exception e) {
                log.error("Failed to cleanup images on error: {}", e.getMessage());
            }
        }
    }

    private void cleanupImageOnError(String imageUrl) {
        if (StringUtils.hasText(imageUrl)) {
            try {
                s3Service.deleteImage(imageUrl);
            } catch (Exception e) {
                log.error("Failed to cleanup image on error: {}", e.getMessage());
            }
        }
    }

    private List<String> collectAllProductImages(Product product) {
        List<String> allImages = new ArrayList<>();

        if (product.getPrimaryImageUrl() != null) {
            allImages.add(product.getPrimaryImageUrl());
        }

        if (product.getAdditionalImages() != null) {
            allImages.addAll(product.getAdditionalImages());
        }

        return allImages;
    }

    private void updateProductImagesForAdd(Product product, String imageUrl, boolean isPrimary) {
        if (isPrimary) {
            if (product.getPrimaryImageUrl() != null) {
                if (product.getAdditionalImages() == null) {
                    product.setAdditionalImages(new ArrayList<>());
                }
                product.getAdditionalImages().add(0, product.getPrimaryImageUrl());
            }
            product.setPrimaryImageUrl(imageUrl);
        } else {
            if (product.getAdditionalImages() == null) {
                product.setAdditionalImages(new ArrayList<>());
            }
            product.getAdditionalImages().add(imageUrl);
        }
    }

    private boolean removeImageFromProduct(Product product, String imageUrl) {
        boolean wasDeleted = false;

        if (imageUrl.equals(product.getPrimaryImageUrl())) {
            product.setPrimaryImageUrl(null);
            wasDeleted = true;
        }

        if (product.getAdditionalImages() != null && product.getAdditionalImages().remove(imageUrl)) {
            wasDeleted = true;
        }

        return wasDeleted;
    }

    private void ensureProductHasPrimaryImage(Product product) {
        if (product.getPrimaryImageUrl() == null) {
            if (product.getAdditionalImages() != null && !product.getAdditionalImages().isEmpty()) {
                product.setPrimaryImageUrl(product.getAdditionalImages().remove(0));
            } else {
                throw new BusinessLogicException("Cannot delete last image. Product must have at least one image.");
            }
        }
    }

    private ProductImageUploadResponseDTO createImageUploadResponse(
            MultipartFile file, String imageUrl, boolean isPrimary, Product product) {

        int order = isPrimary ? 0 :
                (product.getAdditionalImages() != null ? product.getAdditionalImages().size() : 1);

        return ProductImageUploadResponseDTO.builder()
                .imageUrl(imageUrl)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .isPrimary(isPrimary)
                .order(order)
                .build();
    }

    // ============ EXISTING METHODS (Updated with validation) ============

    private Product createProductFromCreateRequest(ProductCreateRequestDTO dto) {
        Product product = new Product();
        updateProductFieldsFromRest(product, dto);
        return product;
    }

    private void updateProductFieldsFromRest(Product product, ProductCreateRequestDTO dto) {
        product.setReferenceNumber(dto.getReferenceNumber());
        product.setNameEn(dto.getNameEn());
        product.setNameBg(dto.getNameBg());
        product.setDescriptionEn(dto.getDescriptionEn());
        product.setDescriptionBg(dto.getDescriptionBg());
        product.setModel(dto.getModel());
        product.setBarcode(dto.getBarcode());

        Category category = findCategoryByIdOrThrow(dto.getCategoryId());
        product.setCategory(category);

        Manufacturer manufacturer = findManufacturerByIdOrThrow(dto.getManufacturerId());
        product.setManufacturer(manufacturer);

        product.setStatus(ProductStatus.fromCode(dto.getStatus()));
        product.setPriceClient(dto.getPriceClient());
        product.setPricePartner(dto.getPricePartner());
        product.setPricePromo(dto.getPricePromo());
        product.setPriceClientPromo(dto.getPriceClientPromo());
        product.setMarkupPercentage(dto.getMarkupPercentage());

        product.setShow(dto.getShow());
        product.setWarranty(dto.getWarranty());
        product.setWeight(dto.getWeight());
        product.setActive(dto.getActive());
        product.setFeatured(dto.getFeatured());

        product.calculateFinalPrice();

        setParametersFromRest(product, dto.getParameters());
    }

    private void processImageOperations(Product product, ProductImageOperationsDTO imageOperations,
                                        MultipartFile newPrimaryImage, List<MultipartFile> newAdditionalImages,
                                        List<String> newUploadedImages, List<String> imagesToCleanup) {

        // Handle image deletions
        if (imageOperations != null && imageOperations.getImagesToDelete() != null) {
            for (String imageUrl : imageOperations.getImagesToDelete()) {
                removeImageFromProduct(product, imageUrl);
                imagesToCleanup.add(imageUrl);
            }
        }

        // Handle primary image replacement
        if (newPrimaryImage != null && !newPrimaryImage.isEmpty()) {
            if (product.getPrimaryImageUrl() != null) {
                imagesToCleanup.add(product.getPrimaryImageUrl());
            }
            String newPrimaryUrl = uploadImageSafely(newPrimaryImage, "products");
            product.setPrimaryImageUrl(newPrimaryUrl);
            newUploadedImages.add(newPrimaryUrl);
        }

        // Handle additional image uploads
        if (newAdditionalImages != null && !newAdditionalImages.isEmpty()) {
            if (product.getAdditionalImages() == null) {
                product.setAdditionalImages(new ArrayList<>());
            }

            for (MultipartFile additionalImage : newAdditionalImages) {
                if (!additionalImage.isEmpty()) {
                    String additionalUrl = uploadImageSafely(additionalImage, "products");
                    product.getAdditionalImages().add(additionalUrl);
                    newUploadedImages.add(additionalUrl);
                }
            }
        }

        // Handle image reordering
        if (imageOperations != null && imageOperations.getReorderImages() != null) {
            handleImageReordering(product, imageOperations.getReorderImages());
        }
    }

    private void handleImageReordering(Product product, List<ProductImageUpdateDTO> images) {
        Optional<ProductImageUpdateDTO> primaryImage = images.stream()
                .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                .findFirst();

        primaryImage.ifPresent(productImageUpdateDTO ->
                product.setPrimaryImageUrl(productImageUpdateDTO.getImageUrl()));

        List<String> additionalImages = images.stream()
                .filter(img -> !Boolean.TRUE.equals(img.getIsPrimary()))
                .sorted(Comparator.comparing(ProductImageUpdateDTO::getOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(ProductImageUpdateDTO::getImageUrl)
                .collect(Collectors.toList());

        if (product.getAdditionalImages() == null) {
            product.setAdditionalImages(new ArrayList<>());
        } else {
            product.getAdditionalImages().clear();
        }
        product.getAdditionalImages().addAll(additionalImages);
    }

    private void setParametersFromRest(Product product, List<ProductParameterCreateDTO> parameters) {
        if (parameters == null) {
            product.setProductParameters(new HashSet<>());
            return;
        }

        Set<ProductParameter> newProductParameters = new HashSet<>();

        for (ProductParameterCreateDTO paramDto : parameters) {
            Parameter parameter = ExceptionHelper.findOrThrow(
                    parameterRepository.findById(paramDto.getParameterId()).orElse(null),
                    "Parameter", paramDto.getParameterId()
            );

            ParameterOption option = ExceptionHelper.findOrThrow(
                    parameterOptionRepository.findById(paramDto.getParameterOptionId()).orElse(null),
                    "ParameterOption", paramDto.getParameterOptionId()
            );

            if (!option.getParameter().getId().equals(parameter.getId())) {
                throw new ValidationException(
                        String.format("Parameter option %d does not belong to parameter %d",
                                paramDto.getParameterOptionId(), paramDto.getParameterId()));
            }

            ProductParameter pp = new ProductParameter();
            pp.setProduct(product);
            pp.setParameter(parameter);
            pp.setParameterOption(option);
            newProductParameters.add(pp);
        }

        product.setProductParameters(newProductParameters);
    }

    // ============ CONVERSION METHODS ============

    private ProductResponseDTO convertToResponseDTO(Product product, String lang) {
        log.info("=== CONVERTING PRODUCT TO RESPONSE DTO ===");
        log.info("Product ID: {}, Language: {}", product.getId(), lang);
        log.info("ProductParameters count: {}", product.getProductParameters().size());

        ProductResponseDTO dto = new ProductResponseDTO();

        dto.setId(product.getId());
        dto.setNameEn(product.getNameEn());
        dto.setNameBg(product.getNameBg());
        dto.setDescriptionEn(product.getDescriptionEn());
        dto.setDescriptionBg(product.getDescriptionBg());

        dto.setReferenceNumber(product.getReferenceNumber());
        dto.setModel(product.getModel());
        dto.setBarcode(product.getBarcode());

        dto.setPriceClient(product.getPriceClient());
        dto.setPricePartner(product.getPricePartner());
        dto.setPricePromo(product.getPricePromo());
        dto.setPriceClientPromo(product.getPriceClientPromo());
        dto.setMarkupPercentage(product.getMarkupPercentage());
        dto.setFinalPrice(product.getFinalPrice());
        dto.setDiscount(product.getDiscount());

        dto.setActive(product.getActive());
        dto.setFeatured(product.getFeatured());
        dto.setShow(product.getShow());

        // Set proxy image URLs
        if (product.getPrimaryImageUrl() != null) {
            dto.setPrimaryImageUrl("/api/images/product/" + product.getId() + "/primary");
        }

        if (product.getAdditionalImages() != null && !product.getAdditionalImages().isEmpty()) {
            List<String> proxyAdditionalUrls = new ArrayList<>();
            for (int i = 0; i < product.getAdditionalImages().size(); i++) {
                proxyAdditionalUrls.add("/api/images/product/" + product.getId() + "/additional/" + i);
            }
            dto.setAdditionalImages(proxyAdditionalUrls);
        }

        dto.setWarranty(product.getWarranty());
        dto.setWeight(product.getWeight());

        // ========== DETAILED LOGGING FOR SPECIFICATIONS ==========
        log.info("--- PROCESSING SPECIFICATIONS ---");
        Set<ProductParameter> productParams = product.getProductParameters();
        log.info("Raw ProductParameter set size: {}", productParams.size());

        // Log each ProductParameter before processing
        int counter = 1;
        for (ProductParameter pp : productParams) {
            log.info("ProductParam {}: ID={}, Parameter={}, Option={}",
                    counter++, pp.getId(),
                    pp.getParameter() != null ? pp.getParameter().getNameEn() : "NULL",
                    pp.getParameterOption() != null ? pp.getParameterOption().getNameEn() : "NULL");

            if (pp.getParameter() != null) {
                log.info("  Parameter details: ID={}, ExternalID={}, NameEN={}, NameBG={}",
                        pp.getParameter().getId(),
                        pp.getParameter().getExternalId(),
                        pp.getParameter().getNameEn(),
                        pp.getParameter().getNameBg());
            }

            if (pp.getParameterOption() != null) {
                log.info("  Option details: ID={}, ExternalID={}, NameEN={}, NameBG={}",
                        pp.getParameterOption().getId(),
                        pp.getParameterOption().getExternalId(),
                        pp.getParameterOption().getNameEn(),
                        pp.getParameterOption().getNameBg());
            }
        }

        // Filter out null parameters/options
        List<ProductParameter> validParams = productParams.stream()
                .filter(pp -> {
                    boolean valid = pp.getParameter() != null && pp.getParameterOption() != null;
                    if (!valid) {
                        log.warn("Filtered out invalid ProductParameter ID: {} (null parameter or option)", pp.getId());
                    }
                    return valid;
                })
                .toList();

        log.info("Valid ProductParameters after filtering: {}", validParams.size());

        // Process each parameter and log conversion
        Map<Long, ProductParameterResponseDto> uniqueSpecs = new HashMap<>();

        for (ProductParameter pp : validParams) {
            Long parameterId = pp.getParameter().getId();
            log.info("Processing Parameter ID: {}, Name: {}", parameterId, pp.getParameter().getNameEn());

            // Convert to DTO
            ProductParameterResponseDto converted = convertToProductParameterResponse(pp, lang);

            if (converted == null) {
                log.error("convertToProductParameterResponse returned NULL for Parameter ID: {}", parameterId);
                continue;
            }

            log.info("Converted DTO: ParameterID={}, Name={}, Options count={}",
                    converted.getParameterId(), converted.getParameterNameEn(),
                    converted.getOptions() != null ? converted.getOptions().size() : 0);

            // Check if we already have this parameter (duplicate handling)
            if (uniqueSpecs.containsKey(parameterId)) {
                log.warn("DUPLICATE Parameter ID found: {}. Merging options...", parameterId);

                ProductParameterResponseDto existing = uniqueSpecs.get(parameterId);
                List<ParameterOptionResponseDto> combinedOptions = new ArrayList<>();
                combinedOptions.addAll(existing.getOptions());
                combinedOptions.addAll(converted.getOptions());

                log.info("Before merge - Existing options: {}, New options: {}",
                        existing.getOptions().size(), converted.getOptions().size());

                // Remove duplicate options by ID
                List<ParameterOptionResponseDto> uniqueOptions = combinedOptions.stream()
                        .collect(Collectors.toMap(
                                ParameterOptionResponseDto::getId,
                                option -> option,
                                (o1, o2) -> {
                                    log.debug("Duplicate option ID: {}, keeping first", o1.getId());
                                    return o1;
                                }))
                        .values()
                        .stream()
                        .sorted(Comparator.comparing(ParameterOptionResponseDto::getOrder))
                        .toList();

                existing.setOptions(uniqueOptions);
                log.info("After merge - Final options count: {}", uniqueOptions.size());

            } else {
                log.info("Adding new Parameter ID: {} to unique specs", parameterId);
                uniqueSpecs.put(parameterId, converted);
            }
        }

        log.info("Final uniqueSpecs map size: {}", uniqueSpecs.size());

        // Convert to list and set in DTO
        List<ProductParameterResponseDto> finalSpecs = new ArrayList<>(uniqueSpecs.values());
        log.info("Final specifications list size: {}", finalSpecs.size());

        // Log final specifications
        for (ProductParameterResponseDto spec : finalSpecs) {
            log.info("Final Spec: ParameterID={}, Name={}, Options={}",
                    spec.getParameterId(), spec.getParameterNameEn(),
                    spec.getOptions().size());

            for (ParameterOptionResponseDto option : spec.getOptions()) {
                log.info("  Option: ID={}, Name={}", option.getId(), option.getName());
            }
        }

        dto.setSpecifications(finalSpecs);

        log.info("=== SPECIFICATIONS PROCESSING COMPLETE ===");
        log.info("Final DTO specifications count: {}", dto.getSpecifications().size());

        if (product.getCategory() != null) {
            dto.setCategory(convertToCategorySummary(product.getCategory()));
        }

        if (product.getManufacturer() != null) {
            dto.setManufacturer(convertToManufacturerSummary(product.getManufacturer()));
        }

        dto.setCreatedAt(product.getCreatedAt());
        dto.setUpdatedAt(product.getUpdatedAt());
        dto.setOnSale(product.isOnSale());
        dto.setStatus(product.getStatus() != null ? product.getStatus().getCode() : 0);
        dto.setWorkflowId(product.getWorkflowId());

        log.info("=== PRODUCT CONVERSION COMPLETE ===");
        return dto;
    }

    private ProductParameterResponseDto convertToProductParameterResponse(ProductParameter productParameter, String lang) {
        log.debug("=== Converting ProductParameter ===");
        log.debug("ProductParameter ID: {}", productParameter.getId());

        if (productParameter.getParameter() == null) {
            log.error("Parameter is NULL for ProductParameter ID: {}", productParameter.getId());
            return null;
        }

        if (productParameter.getParameterOption() == null) {
            log.error("ParameterOption is NULL for ProductParameter ID: {}", productParameter.getId());
            return null;
        }

        Parameter parameter = productParameter.getParameter();
        ParameterOption option = productParameter.getParameterOption();

        log.debug("Parameter: ID={}, External ID={}, Name EN={}, Name BG={}",
                parameter.getId(), parameter.getExternalId(), parameter.getNameEn(), parameter.getNameBg());
        log.debug("Option: ID={}, External ID={}, Name EN={}, Name BG={}",
                option.getId(), option.getExternalId(), option.getNameEn(), option.getNameBg());

        // Използвай ParameterMapper за option
        ParameterOptionResponseDto optionDto = parameterMapper.toOptionResponseDto(option, lang);
        log.debug("Mapped option DTO: ID={}, Name={}", optionDto.getId(), optionDto.getName());

        ProductParameterResponseDto result = ProductParameterResponseDto.builder()
                .parameterId(parameter.getId())
                .parameterNameEn(parameter.getNameEn())
                .parameterNameBg(parameter.getNameBg())
                .options(List.of(optionDto))  // Само избраната опция
                .build();

        log.debug("Built ProductParameterResponseDto: Parameter ID={}, Options count={}",
                result.getParameterId(), result.getOptions().size());

        return result;
    }

    private CategorySummaryDTO convertToCategorySummary(Category category) {
        return CategorySummaryDTO.builder()
                .id(category.getId())
                .nameEn(category.getNameEn())
                .nameBg(category.getNameBg())
                .slug(category.getSlug())
                .show(category.getShow())
                .build();
    }

    private ManufacturerSummaryDto convertToManufacturerSummary(Manufacturer manufacturer) {
        return ManufacturerSummaryDto.builder()
                .id(manufacturer.getId())
                .name(manufacturer.getName())
                .build();
    }

    // ============ REMAINING READ METHODS (with validation) ============

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getFeaturedProducts(Pageable pageable, String lang) {
        log.debug("Fetching featured products");

        validatePaginationParameters(pageable);
        validateLanguage(lang);

        return ExceptionHelper.wrapDatabaseOperation(() ->
                        productRepository.findByActiveTrueAndFeaturedTrue(pageable)
                                .map(p -> convertToResponseDTO(p, lang)),
                "fetch featured products"
        );
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getProductsOnSale(Pageable pageable, String lang) {
        log.debug("Fetching products on sale");

        validatePaginationParameters(pageable);
        validateLanguage(lang);

        return ExceptionHelper.wrapDatabaseOperation(() ->
                        productRepository.findProductsOnSale(pageable)
                                .map(p -> convertToResponseDTO(p, lang)),
                "fetch products on sale"
        );
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> filterProducts(Long categoryId, Long brandId,
                                                   BigDecimal minPrice, BigDecimal maxPrice,
                                                   ProductStatus status, Boolean onSale, String query,
                                                   Pageable pageable, String lang) {
        log.debug("Filtering products with multiple criteria");

        String context = ExceptionHelper.createErrorContext(
                "filterProducts", "Product", null,
                String.format("categoryId: %s, brandId: %s, query: %s", categoryId, brandId, query));

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            validatePaginationParameters(pageable);
            validateLanguage(lang);

            if (categoryId != null) {
                validateCategoryId(categoryId);
            }

            if (brandId != null) {
                validateManufacturerId(brandId);
            }

            if (StringUtils.hasText(query)) {
                validateSearchQuery(query);
            }

            if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidationException("Minimum price cannot be negative");
            }

            if (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidationException("Maximum price cannot be negative");
            }

            if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
                throw new ValidationException("Minimum price cannot be greater than maximum price");
            }

            return productRepository.findProductsWithFilters(categoryId, brandId, minPrice, maxPrice,
                            status, onSale, query, pageable)
                    .map(p -> convertToResponseDTO(p, lang));
        }, context);
    }
}