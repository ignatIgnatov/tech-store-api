package com.techstore.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techstore.dto.*;
import com.techstore.entity.CategorySpecificationTemplate;
import com.techstore.entity.Product;
import com.techstore.entity.ProductSpecification;
import com.techstore.entity.Category;
import com.techstore.entity.Brand;
import com.techstore.exception.BusinessLogicException;
import com.techstore.repository.CategorySpecificationTemplateRepository;
import com.techstore.repository.ProductRepository;
import com.techstore.repository.ProductSpecificationRepository;
import com.techstore.repository.CategoryRepository;
import com.techstore.repository.BrandRepository;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.exception.DuplicateResourceException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductSpecificationRepository specificationRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final CategorySpecificationTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    // ===== READ OPERATIONS =====

    @Transactional(readOnly = true)
    public Page<ProductSummaryDTO> getAllProducts(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable)
                .map(this::convertToSummaryDTO);
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return convertToResponseDTO(product);
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO getProductBySku(String sku) {
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with SKU: " + sku));
        return convertToResponseDTO(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryDTO> getProductsByCategory(Long categoryId, Pageable pageable) {
        return productRepository.findByActiveTrueAndCategoryId(categoryId, pageable)
                .map(this::convertToSummaryDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryDTO> getProductsByBrand(Long brandId, Pageable pageable) {
        return productRepository.findByActiveTrueAndBrandId(brandId, pageable)
                .map(this::convertToSummaryDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryDTO> getFeaturedProducts(Pageable pageable) {
        return productRepository.findByActiveTrueAndFeaturedTrue(pageable)
                .map(this::convertToSummaryDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryDTO> getProductsOnSale(Pageable pageable) {
        return productRepository.findProductsOnSale(pageable)
                .map(this::convertToSummaryDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryDTO> searchProducts(String query, Pageable pageable) {
        return productRepository.searchProducts(query, pageable)
                .map(this::convertToSummaryDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryDTO> filterProducts(Long categoryId, Long brandId,
                                                  BigDecimal minPrice, BigDecimal maxPrice,
                                                  Boolean inStock, Boolean onSale, String query,
                                                  Pageable pageable) {
        return productRepository.findProductsWithFilters(categoryId, brandId, minPrice, maxPrice,
                        inStock, onSale, query, pageable)
                .map(this::convertToSummaryDTO);
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryDTO> getRelatedProducts(Long productId, int limit) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        Pageable pageable = Pageable.ofSize(limit);
        return productRepository.findRelatedProducts(productId, product.getCategory().getId(),
                        product.getBrand().getId(), pageable)
                .stream()
                .map(this::convertToSummaryDTO)
                .collect(Collectors.toList());
    }

    // ===== WRITE OPERATIONS =====

    public ProductResponseDTO createProduct(ProductRequestDTO requestDTO) {
        log.info("Creating product with specification validation: {}", requestDTO.getSku());

        // Validate SKU uniqueness
        if (productRepository.existsBySku(requestDTO.getSku())) {
            throw new DuplicateResourceException("Product with SKU '" + requestDTO.getSku() + "' already exists");
        }

        // Get category templates for validation
        List<CategorySpecificationTemplate> requiredTemplates =
                templateRepository.findByCategoryIdAndRequiredTrueOrderBySortOrderAsc(requestDTO.getCategoryId());

        // Validate required specifications are provided
        validateRequiredSpecifications(requestDTO.getSpecifications(), requiredTemplates);

        // Create product
        Product product = convertToEntity(requestDTO);
        product = productRepository.save(product);

        // Save validated specifications using the corrected method ✅
        if (requestDTO.getSpecifications() != null) {
            saveProductSpecifications(product, requestDTO.getSpecifications());
        }

        log.info("Product created successfully with id: {}", product.getId());
        return convertToResponseDTO(product);
    }

    private void validateRequiredSpecifications(List<ProductSpecificationRequestDTO> specs,
                                                List<CategorySpecificationTemplate> requiredTemplates) {
        if (specs == null) specs = Collections.emptyList();

        Set<String> providedSpecs = specs.stream()
                .map(ProductSpecificationRequestDTO::getSpecName)
                .collect(Collectors.toSet());

        List<String> missingSpecs = requiredTemplates.stream()
                .map(CategorySpecificationTemplate::getSpecName)
                .filter(specName -> !providedSpecs.contains(specName))
                .collect(Collectors.toList());

        if (!missingSpecs.isEmpty()) {
            throw new BusinessLogicException("Missing required specifications: " + String.join(", ", missingSpecs));
        }
    }

    //without specifications validations
//    public ProductResponseDTO createProduct(ProductRequestDTO requestDTO) {
//        log.info("Creating new product with SKU: {}", requestDTO.getSku());
//
//        // Validate SKU uniqueness
//        if (productRepository.existsBySku(requestDTO.getSku())) {
//            throw new DuplicateResourceException("Product with SKU '" + requestDTO.getSku() + "' already exists");
//        }
//
//        Product product = convertToEntity(requestDTO);
//        product = productRepository.save(product);
//
//        // Save specifications
//        if (requestDTO.getSpecifications() != null) {
//            saveProductSpecifications(product, requestDTO.getSpecifications());
//        }
//
//        log.info("Product created successfully with id: {}", product.getId());
//        return convertToResponseDTO(product);
//    }

    public ProductResponseDTO updateProduct(Long id, ProductRequestDTO requestDTO) {
        log.info("Updating product with id: {}", id);

        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        // Validate SKU uniqueness (excluding current product)
        if (productRepository.existsBySkuAndIdNot(requestDTO.getSku(), id)) {
            throw new DuplicateResourceException("Product with SKU '" + requestDTO.getSku() + "' already exists");
        }

        updateProductFromDTO(existingProduct, requestDTO);
        Product updatedProduct = productRepository.save(existingProduct);

        // Update specifications
        specificationRepository.deleteByProductId(id);
        if (requestDTO.getSpecifications() != null) {
            saveProductSpecifications(updatedProduct, requestDTO.getSpecifications());
        }

        log.info("Product updated successfully with id: {}", id);
        return convertToResponseDTO(updatedProduct);
    }

    public void deleteProduct(Long id) {
        log.info("Deleting product with id: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        // Soft delete by setting active to false
        product.setActive(false);
        productRepository.save(product);

        log.info("Product soft deleted successfully with id: {}", id);
    }

    public void permanentDeleteProduct(Long id) {
        log.info("Permanently deleting product with id: {}", id);

        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }

        productRepository.deleteById(id);
        log.info("Product permanently deleted successfully with id: {}", id);
    }

    // ===== UTILITY METHODS =====


// ===== CORRECTED PRODUCT SPECIFICATION SAVE METHOD =====

// Replace the old method in ProductService with this corrected version:

    private void saveProductSpecifications(Product product, List<ProductSpecificationRequestDTO> specDTOs) {
        // Get all templates for this category to map spec names to templates
        Map<String, CategorySpecificationTemplate> templateMap =
                templateRepository.findByCategoryIdOrderBySortOrderAscSpecNameAsc(product.getCategory().getId())
                        .stream()
                        .collect(Collectors.toMap(CategorySpecificationTemplate::getSpecName, t -> t));

        List<ProductSpecification> specifications = specDTOs.stream()
                .map(specDTO -> {
                    // Find the template for this specification
                    CategorySpecificationTemplate template = templateMap.get(specDTO.getSpecName());
                    if (template == null) {
                        throw new BusinessLogicException("Invalid specification: " + specDTO.getSpecName() +
                                " is not defined for this category");
                    }

                    // Validate specification value against template
                    validateSpecificationValue(specDTO.getSpecValue(), template);

                    // Create ProductSpecification with template reference
                    ProductSpecification spec = new ProductSpecification();
                    spec.setSpecValue(specDTO.getSpecValue());
                    spec.setSpecValueSecondary(specDTO.getSpecValueSecondary()); // For ranges
                    spec.setSortOrder(template.getSortOrder()); // Use template's sort order
                    spec.setProduct(product);
                    spec.setTemplate(template); // Set template reference instead of individual fields
                    return spec;
                })
                .collect(Collectors.toList());

        specificationRepository.saveAll(specifications);
    }

    private void validateSpecificationValue(String value, CategorySpecificationTemplate template) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessLogicException("Specification value cannot be empty for " + template.getSpecName());
        }

        switch (template.getType()) {
            case NUMBER:
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new BusinessLogicException("Invalid number value for " + template.getSpecName() + ": " + value);
                }
                break;

            case DECIMAL:
                try {
                    new BigDecimal(value);
                } catch (NumberFormatException e) {
                    throw new BusinessLogicException("Invalid decimal value for " + template.getSpecName() + ": " + value);
                }
                break;

            case BOOLEAN:
                if (!Arrays.asList("true", "false", "yes", "no", "1", "0").contains(value.toLowerCase())) {
                    throw new BusinessLogicException("Invalid boolean value for " + template.getSpecName() +
                            ". Must be: true/false, yes/no, or 1/0");
                }
                break;

            case DROPDOWN:
            case MULTI_SELECT:
                if (template.getAllowedValues() != null) {
                    List<String> allowedValues = parseAllowedValues(template.getAllowedValues());
                    if (!allowedValues.contains(value)) {
                        throw new BusinessLogicException("Invalid value for " + template.getSpecName() +
                                ". Allowed values: " + String.join(", ", allowedValues));
                    }
                }
                break;

            case EMAIL:
                if (!value.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                    throw new BusinessLogicException("Invalid email format for " + template.getSpecName());
                }
                break;

            case URL:
                try {
                    new URL(value);
                } catch (MalformedURLException e) {
                    throw new BusinessLogicException("Invalid URL format for " + template.getSpecName());
                }
                break;

            case COLOR:
                if (!value.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$")) {
                    throw new BusinessLogicException("Invalid color format for " + template.getSpecName() +
                            ". Use hex format like #FF0000");
                }
                break;

            // TEXT and other types - no specific validation needed
            default:
                break;
        }
    }

    private List<String> parseAllowedValues(String allowedValuesJson) {
        try {
            return objectMapper.readValue(allowedValuesJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse allowed values: {}", allowedValuesJson);
            return Collections.emptyList();
        }
    }

    private Product convertToEntity(ProductRequestDTO dto) {
        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + dto.getCategoryId()));

        Brand brand = brandRepository.findById(dto.getBrandId())
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + dto.getBrandId()));

        Product product = new Product();
        product.setNameEn(dto.getName());
        product.setSku(dto.getSku());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setDiscount(dto.getDiscount());
        product.setStockQuantity(dto.getStockQuantity());
        product.setActive(dto.getActive());
        product.setFeatured(dto.getFeatured());
        product.setImageUrl(dto.getImageUrl());
        product.setAdditionalImages(dto.getAdditionalImages());
        product.setWarranty(dto.getWarranty());
        product.setWeight(dto.getWeight());
        product.setDimensions(dto.getDimensions());
        product.setCategory(category);
        product.setBrand(brand);

        return product;
    }

    private void updateProductFromDTO(Product product, ProductRequestDTO dto) {
        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + dto.getCategoryId()));

        Brand brand = brandRepository.findById(dto.getBrandId())
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + dto.getBrandId()));

        product.setNameEn(dto.getName());
        product.setSku(dto.getSku());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setDiscount(dto.getDiscount());
        product.setStockQuantity(dto.getStockQuantity());
        product.setActive(dto.getActive());
        product.setFeatured(dto.getFeatured());
        product.setImageUrl(dto.getImageUrl());
        product.setAdditionalImages(dto.getAdditionalImages());
        product.setWarranty(dto.getWarranty());
        product.setWeight(dto.getWeight());
        product.setDimensions(dto.getDimensions());
        product.setCategory(category);
        product.setBrand(brand);
    }

    private ProductResponseDTO convertToResponseDTO(Product product) {
        return ProductResponseDTO.builder()
                .id(product.getId())
                .name(product.getNameEn())
                .sku(product.getSku())
                .description(product.getDescription())
                .price(product.getPrice())
                .discount(product.getDiscount())
                .discountedPrice(product.getDiscountedPrice())
                .stockQuantity(product.getStockQuantity())
                .active(product.getActive())
                .featured(product.getFeatured())
                .imageUrl(product.getImageUrl())
                .additionalImages(product.getAdditionalImages())
                .warranty(product.getWarranty())
                .weight(product.getWeight())
                .dimensions(product.getDimensions())
                .category(convertToCategorySummary(product.getCategory()))
                .brand(convertToBrandSummary(product.getBrand()))
                .specifications(convertToSpecificationDTOs(product.getSpecifications()))
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .inStock(product.isInStock())
                .onSale(product.isOnSale())
                .build();
    }

    private ProductSummaryDTO convertToSummaryDTO(Product product) {
        return ProductSummaryDTO.builder()
                .id(product.getId())
                .name(product.getNameEn())
                .sku(product.getSku())
                .price(product.getPrice())
                .discount(product.getDiscount())
                .discountedPrice(product.getDiscountedPrice())
                .stockQuantity(product.getStockQuantity())
                .active(product.getActive())
                .featured(product.getFeatured())
                .imageUrl(product.getImageUrl())
                .categoryName(product.getCategory().getNameEn())
                .brandName(product.getBrand().getName())
                .inStock(product.isInStock())
                .onSale(product.isOnSale())
                .build();
    }

    private CategorySummaryDTO convertToCategorySummary(Category category) {
        return CategorySummaryDTO.builder()
                .id(category.getId())
                .name(category.getNameEn())
                .slug(category.getSlug())
                .active(category.getActive())
                .build();
    }

    private BrandSummaryDTO convertToBrandSummary(Brand brand) {
        return BrandSummaryDTO.builder()
                .id(brand.getId())
                .name(brand.getName())
                .slug(brand.getSlug())
                .logoUrl(brand.getLogoUrl())
                .active(brand.getActive())
                .build();
    }

    private List<ProductSpecificationDTO> convertToSpecificationDTOs(List<ProductSpecification> specifications) {
        return specifications.stream()
                .map(spec -> ProductSpecificationDTO.builder()
                        .id(spec.getId())
                        .specName(spec.getSpecName())
                        .specValue(spec.getSpecValue())
                        .specUnit(spec.getSpecUnit())
                        .specGroup(spec.getSpecGroup())
                        .sortOrder(spec.getSortOrder())
                        .formattedValue(spec.getFormattedValue())
                        .build())
                .collect(Collectors.toList());
    }
}