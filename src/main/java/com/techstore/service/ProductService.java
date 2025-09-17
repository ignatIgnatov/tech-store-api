package com.techstore.service;

import com.techstore.dto.ProductResponseDTO;
import com.techstore.dto.external.ImageDto;
import com.techstore.dto.external.ProductRequestDto;
import com.techstore.dto.request.ParameterValueRequestDto;
import com.techstore.dto.request.ProductCreateRequestDTO;
import com.techstore.dto.request.ProductImageOperationsDTO;
import com.techstore.dto.request.ProductImageUpdateDTO;
import com.techstore.dto.request.ProductParameterCreateDTO;
import com.techstore.dto.request.ProductUpdateRequestDTO;
import com.techstore.dto.response.CategorySummaryDTO;
import com.techstore.dto.response.ManufacturerSummaryDto;
import com.techstore.dto.response.ProductImageUploadResponseDTO;
import com.techstore.entity.Category;
import com.techstore.entity.Manufacturer;
import com.techstore.entity.Parameter;
import com.techstore.entity.ParameterOption;
import com.techstore.entity.Product;
import com.techstore.entity.ProductParameter;
import com.techstore.enums.ProductStatus;
import com.techstore.exception.BusinessLogicException;
import com.techstore.exception.DuplicateResourceException;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.repository.CategoryRepository;
import com.techstore.repository.ManufacturerRepository;
import com.techstore.repository.ParameterOptionRepository;
import com.techstore.repository.ParameterRepository;
import com.techstore.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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

    @Transactional
    public ProductResponseDTO createProductWithImages(
            ProductCreateRequestDTO productData,
            MultipartFile primaryImage,
            List<MultipartFile> additionalImages) {

        if (primaryImage == null || primaryImage.isEmpty()) {
            throw new BusinessLogicException("Primary image is required for product creation");
        }

        if (productRepository.existsByReferenceNumberIgnoreCase(productData.getReferenceNumber())) {
            throw new DuplicateResourceException("Product already exists with reference number: " + productData.getReferenceNumber());
        }

        List<String> uploadedImageUrls = new ArrayList<>();

        try {
            log.debug("Uploading primary image for product: {}", productData.getReferenceNumber());
            String primaryImageUrl = s3Service.uploadProductImage(primaryImage, "products");
            uploadedImageUrls.add(primaryImageUrl);

            List<String> additionalImageUrls = new ArrayList<>();
            if (additionalImages != null && !additionalImages.isEmpty()) {
                log.debug("Uploading {} additional images", additionalImages.size());
                for (MultipartFile additionalImage : additionalImages) {
                    if (!additionalImage.isEmpty()) {
                        String additionalImageUrl = s3Service.uploadProductImage(additionalImage, "products");
                        additionalImageUrls.add(additionalImageUrl);
                        uploadedImageUrls.add(additionalImageUrl);
                    }
                }
            }

            Product product = new Product();
            updateProductFieldsFromRest(product, productData);

            product.setPrimaryImageUrl(primaryImageUrl);
            if (!additionalImageUrls.isEmpty()) {
                product.setAdditionalImages(additionalImageUrls);
            }

            product = productRepository.save(product);

            log.info("Product created successfully with id: {} and {} images",
                    product.getId(), uploadedImageUrls.size());

            return convertToResponseDTO(product);

        } catch (Exception e) {
            log.error("Product creation failed, cleaning up uploaded images", e);
            s3Service.deleteImages(uploadedImageUrls);
            throw e;
        }
    }

    @Transactional
    public ProductResponseDTO updateProductWithImages(
            Long id,
            ProductUpdateRequestDTO productData,
            MultipartFile newPrimaryImage,
            List<MultipartFile> newAdditionalImages,
            ProductImageOperationsDTO imageOperations) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if (!product.getReferenceNumber().equalsIgnoreCase(productData.getReferenceNumber()) &&
                productRepository.existsByReferenceNumberIgnoreCase(productData.getReferenceNumber())) {
            throw new DuplicateResourceException("Product already exists with reference number: " + productData.getReferenceNumber());
        }

        List<String> newUploadedImages = new ArrayList<>();
        List<String> imagesToCleanup = new ArrayList<>();

        try {
            if (imageOperations != null && imageOperations.getImagesToDelete() != null) {
                for (String imageUrl : imageOperations.getImagesToDelete()) {
                    if (imageUrl.equals(product.getPrimaryImageUrl())) {
                        product.setPrimaryImageUrl(null);
                    }
                    if (product.getAdditionalImages() != null) {
                        product.getAdditionalImages().remove(imageUrl);
                    }
                    imagesToCleanup.add(imageUrl);
                }
            }

            if (newPrimaryImage != null && !newPrimaryImage.isEmpty()) {
                if (product.getPrimaryImageUrl() != null) {
                    imagesToCleanup.add(product.getPrimaryImageUrl());
                }
                String newPrimaryUrl = s3Service.uploadProductImage(newPrimaryImage, "products");
                product.setPrimaryImageUrl(newPrimaryUrl);
                newUploadedImages.add(newPrimaryUrl);
            }

            if (newAdditionalImages != null && !newAdditionalImages.isEmpty()) {
                if (product.getAdditionalImages() == null) {
                    product.setAdditionalImages(new ArrayList<>());
                }

                for (MultipartFile additionalImage : newAdditionalImages) {
                    if (!additionalImage.isEmpty()) {
                        String additionalUrl = s3Service.uploadProductImage(additionalImage, "products");
                        product.getAdditionalImages().add(additionalUrl);
                        newUploadedImages.add(additionalUrl);
                    }
                }
            }

            if (imageOperations != null && imageOperations.getReorderImages() != null) {
                handleImageReordering(product, imageOperations.getReorderImages());
            }

            if (product.getPrimaryImageUrl() == null) {
                if (product.getAdditionalImages() != null && !product.getAdditionalImages().isEmpty()) {
                    product.setPrimaryImageUrl(product.getAdditionalImages().remove(0));
                } else {
                    s3Service.deleteImages(newUploadedImages);
                    throw new BusinessLogicException("Product must have at least one image");
                }
            }

            updateProductFieldsFromRest(product, productData);

            product = productRepository.save(product);

            if (!imagesToCleanup.isEmpty()) {
                s3Service.deleteImages(imagesToCleanup);
            }

            log.info("Product updated successfully with id: {}", product.getId());
            return convertToResponseDTO(product);

        } catch (Exception e) {
            log.error("Product update failed, cleaning up new uploads", e);
            s3Service.deleteImages(newUploadedImages);
            throw e;
        }
    }

    @Transactional
    public ProductImageUploadResponseDTO addImageToProduct(Long productId, MultipartFile file, boolean isPrimary) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        String imageUrl = s3Service.uploadProductImage(file, "products");

        try {
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

            productRepository.save(product);

            return ProductImageUploadResponseDTO.builder()
                    .imageUrl(imageUrl)
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .contentType(file.getContentType())
                    .isPrimary(isPrimary)
                    .order(isPrimary ? 0 : product.getAdditionalImages().size())
                    .build();

        } catch (Exception e) {
            s3Service.deleteImage(imageUrl);
            throw e;
        }
    }

    @Transactional
    public void deleteProductImage(Long productId, String imageUrl) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        boolean wasDeleted = false;

        if (imageUrl.equals(product.getPrimaryImageUrl())) {
            product.setPrimaryImageUrl(null);
            wasDeleted = true;
        }

        if (product.getAdditionalImages() != null && product.getAdditionalImages().remove(imageUrl)) {
            wasDeleted = true;
        }

        if (!wasDeleted) {
            throw new ResourceNotFoundException("Image not found for this product");
        }

        if (product.getPrimaryImageUrl() == null) {
            if (product.getAdditionalImages() != null && !product.getAdditionalImages().isEmpty()) {
                product.setPrimaryImageUrl(product.getAdditionalImages().remove(0));
            } else {
                throw new BusinessLogicException("Cannot delete last image. Product must have at least one image.");
            }
        }

        productRepository.save(product);

        s3Service.deleteImage(imageUrl);

        log.info("Deleted image {} from product {}", imageUrl, productId);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getAllProducts(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable)
                .map(this::convertToResponseDTO);
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return convertToResponseDTO(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getProductsByCategory(Long categoryId, Pageable pageable) {
        return productRepository.findByActiveTrueAndCategoryId(categoryId, pageable)
                .map(this::convertToResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getProductsByBrand(Long brandId, Pageable pageable) {
        return productRepository.findByActiveTrueAndManufacturerId(brandId, pageable)
                .map(this::convertToResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getFeaturedProducts(Pageable pageable) {
        return productRepository.findByActiveTrueAndFeaturedTrue(pageable)
                .map(this::convertToResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getProductsOnSale(Pageable pageable) {
        return productRepository.findProductsOnSale(pageable)
                .map(this::convertToResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> searchProducts(String query, Pageable pageable) {
        return productRepository.searchProducts(query, pageable)
                .map(this::convertToResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> filterProducts(Long categoryId, Long brandId,
                                                   BigDecimal minPrice, BigDecimal maxPrice,
                                                   ProductStatus status, Boolean onSale, String query,
                                                   Pageable pageable) {
        return productRepository.findProductsWithFilters(categoryId, brandId, minPrice, maxPrice,
                        status, onSale, query, pageable)
                .map(this::convertToResponseDTO);
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getRelatedProducts(Long productId, int limit) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        Pageable pageable = Pageable.ofSize(limit);
        return productRepository.findRelatedProducts(productId, product.getCategory().getId(),
                        product.getManufacturer().getId(), pageable)
                .stream()
                .map(this::convertToResponseDTO)
                .toList();
    }

    @Transactional
    public ProductResponseDTO createProductRest(ProductCreateRequestDTO dto) {
        if (productRepository.existsByReferenceNumberIgnoreCase(dto.getReferenceNumber())) {
            throw new DuplicateResourceException("Product already exists with reference number: " + dto.getReferenceNumber());
        }

        Product product = new Product();
        updateProductFieldsFromRest(product, dto);
        product = productRepository.save(product);

        log.info("Created product via REST API with id: {} and reference: {}", product.getId(), product.getReferenceNumber());
        return convertToResponseDTO(product);
    }

    @Transactional
    public ProductResponseDTO updateProductRest(Long id, ProductUpdateRequestDTO dto) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if (!product.getReferenceNumber().equalsIgnoreCase(dto.getReferenceNumber()) &&
                productRepository.existsByReferenceNumberIgnoreCase(dto.getReferenceNumber())) {
            throw new DuplicateResourceException("Product already exists with reference number: " + dto.getReferenceNumber());
        }

        if (dto.getImagesToDelete() != null && !dto.getImagesToDelete().isEmpty()) {
            handleImageDeletions(product, dto.getImagesToDelete());
        }

        updateProductFieldsFromRest(product, dto);

        if (dto.getImages() != null && !dto.getImages().isEmpty()) {
            handleImageReordering(product, dto.getImages());
        }

        product = productRepository.save(product);

        log.info("Updated product via REST API with id: {} and reference: {}", product.getId(), product.getReferenceNumber());
        return convertToResponseDTO(product);
    }

    @Transactional
    public ProductResponseDTO reorderProductImages(Long productId, List<ProductImageUpdateDTO> images) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        handleImageReordering(product, images);
        product = productRepository.save(product);

        return convertToResponseDTO(product);
    }

    private void updateProductFieldsFromExternal(Product product, ProductRequestDto dto) {
        setNamesToProduct(product, dto);
        setDescriptionToProduct(product, dto);
        product.setReferenceNumber(dto.getReferenceNumber());
        product.setModel(dto.getModel());
        product.setBarcode(dto.getBarcode());
        product.setExternalId(dto.getId());
        product.setWorkflowId(dto.getIdWF());

        setCategoryToProduct(product, dto);
        setManufacturer(product, dto);

        product.setStatus(ProductStatus.fromCode(dto.getStatus()));
        product.setPriceClient(dto.getPriceClient());
        product.setPricePartner(dto.getPricePartner());
        product.setPricePromo(dto.getPricePromo());
        product.setPriceClientPromo(dto.getPriceClientPromo());
        product.setMarkupPercentage(dto.getMarkupPercentage());
        product.calculateFinalPrice();

        setImagesToProduct(product, dto);
        setParametersToProduct(product, dto);
    }

    private void setManufacturer(Product product, ProductRequestDto dto) {
        Manufacturer manufacturer = manufacturerRepository.findById(dto.getManufacturerId())
                .orElseThrow(() -> new ResourceNotFoundException("Manufacturer not found: " + dto.getManufacturerId()));
        product.setManufacturer(manufacturer);
    }

    private static void setImagesToProduct(Product product, ProductRequestDto extProduct) {
        if (extProduct.getImages() != null && !extProduct.getImages().isEmpty()) {
            product.setPrimaryImageUrl(extProduct.getImages().get(0).getHref());
            product.setAdditionalImages(
                    extProduct.getImages().stream().skip(1).map(ImageDto::getHref).toList()
            );
        }
    }

    private void setCategoryToProduct(Product product, ProductRequestDto extProduct) {
        categoryRepository.findByExternalId(extProduct.getCategories().get(0).getId())
                .ifPresent(product::setCategory);
    }

    private static void setDescriptionToProduct(Product product, ProductRequestDto extProduct) {
        if (extProduct.getDescription() != null) {
            extProduct.getDescription().forEach(desc -> {
                switch (desc.getLanguageCode()) {
                    case "bg" -> product.setDescriptionBg(desc.getText());
                    case "en" -> product.setDescriptionEn(desc.getText());
                }
            });
        }
    }

    private static void setNamesToProduct(Product product, ProductRequestDto extProduct) {
        if (extProduct.getName() != null) {
            extProduct.getName().forEach(name -> {
                switch (name.getLanguageCode()) {
                    case "bg" -> product.setNameBg(name.getText());
                    case "en" -> product.setNameEn(name.getText());
                }
            });
        }
    }

    @Transactional
    public void deleteProduct(Long id) {
        log.info("Deleting product with id: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        List<String> allImages = new ArrayList<>();
        if (product.getPrimaryImageUrl() != null) {
            allImages.add(product.getPrimaryImageUrl());
        }
        if (product.getAdditionalImages() != null) {
            allImages.addAll(product.getAdditionalImages());
        }
        s3Service.deleteImages(allImages);

        product.setActive(false);
        productRepository.save(product);

        log.info("Product soft deleted successfully with id: {}", id);
    }

    @Transactional
    public void permanentDeleteProduct(Long id) {
        log.info("Permanently deleting product with id: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        List<String> allImages = new ArrayList<>();
        if (product.getPrimaryImageUrl() != null) {
            allImages.add(product.getPrimaryImageUrl());
        }
        if (product.getAdditionalImages() != null) {
            allImages.addAll(product.getAdditionalImages());
        }
        s3Service.deleteImages(allImages);

        productRepository.deleteById(id);
        log.info("Product permanently deleted successfully with id: {}", id);
    }

    private ProductResponseDTO convertToResponseDTO(Product product) {
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

        dto.setPrimaryImageUrl(product.getPrimaryImageUrl());
        dto.setAdditionalImages(product.getAdditionalImages());

        dto.setWarranty(product.getWarranty());
        dto.setWeight(product.getWeight());
        dto.setSpecifications(product.getProductParameters().stream().toList());

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

        return dto;
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

    private void setParametersToProduct(Product product, ProductRequestDto extProduct) {
        if (extProduct.getParameters() != null && product.getCategory() != null) {
            product.getProductParameters().clear();

            for (ParameterValueRequestDto paramValue : extProduct.getParameters()) {
                parameterRepository.findByExternalIdAndCategoryId(paramValue.getParameterId(), product.getCategory().getId())
                        .ifPresent(parameter ->
                                parameterOptionRepository.findByExternalIdAndParameterId(paramValue.getOptionId(), parameter.getId())
                                        .ifPresent(option -> {
                                            ProductParameter pp = new ProductParameter();
                                            pp.setProduct(product);
                                            pp.setParameter(parameter);
                                            pp.setParameterOption(option);
                                            product.getProductParameters().add(pp);
                                        })
                        );
            }
        }
    }

    private void handleImageReordering(Product product, List<ProductImageUpdateDTO> images) {
        Optional<ProductImageUpdateDTO> primaryImage = images.stream()
                .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                .findFirst();

        primaryImage.ifPresent(productImageUpdateDTO -> product.setPrimaryImageUrl(productImageUpdateDTO.getImageUrl()));

        List<String> additionalImages = images.stream()
                .filter(img -> !Boolean.TRUE.equals(img.getIsPrimary()))
                .sorted(Comparator.comparing(ProductImageUpdateDTO::getOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(ProductImageUpdateDTO::getImageUrl)
                .collect(Collectors.toList());

        if (product.getAdditionalImages() == null) {
            product.setAdditionalImages(new ArrayList<>());
        } else {
            product.getAdditionalImages().clear();
        }
        product.getAdditionalImages().addAll(additionalImages);
    }

    private void handleImageDeletions(Product product, List<String> imagesToDelete) {
        for (String imageUrl : imagesToDelete) {
            if (imageUrl.equals(product.getPrimaryImageUrl())) {
                product.setPrimaryImageUrl(null);
            }

            if (product.getAdditionalImages() != null) {
                product.getAdditionalImages().remove(imageUrl);
            }

            s3Service.deleteImage(imageUrl);
        }
    }

    private void setParametersFromRest(Product product, List<ProductParameterCreateDTO> parameters) {
        if (parameters == null) {
            product.setProductParameters(new HashSet<>());
            return;
        }

        Set<ProductParameter> newProductParameters = new HashSet<>();

        for (ProductParameterCreateDTO paramDto : parameters) {
            Parameter parameter = parameterRepository.findById(paramDto.getParameterId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parameter not found with id: " + paramDto.getParameterId()));

            ParameterOption option = parameterOptionRepository.findById(paramDto.getParameterOptionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parameter option not found with id: " + paramDto.getParameterOptionId()));

            if (!option.getParameter().getId().equals(parameter.getId())) {
                throw new IllegalArgumentException("Parameter option " + paramDto.getParameterOptionId() +
                        " does not belong to parameter " + paramDto.getParameterId());
            }

            ProductParameter pp = new ProductParameter();
            pp.setProduct(product);
            pp.setParameter(parameter);
            pp.setParameterOption(option);
            newProductParameters.add(pp);
        }

        product.setProductParameters(newProductParameters);
    }

    private void updateProductFieldsFromRest(Product product, ProductCreateRequestDTO dto) {
        product.setReferenceNumber(dto.getReferenceNumber());
        product.setNameEn(dto.getNameEn());
        product.setNameBg(dto.getNameBg());
        product.setDescriptionEn(dto.getDescriptionEn());
        product.setDescriptionBg(dto.getDescriptionBg());
        product.setModel(dto.getModel());
        product.setBarcode(dto.getBarcode());

        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + dto.getCategoryId()));
        product.setCategory(category);

        Manufacturer manufacturer = manufacturerRepository.findById(dto.getManufacturerId())
                .orElseThrow(() -> new ResourceNotFoundException("Manufacturer not found with id: " + dto.getManufacturerId()));
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
}