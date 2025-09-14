package com.techstore.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techstore.dto.CategorySummaryDTO;
import com.techstore.dto.ManufacturerSummaryDto;
import com.techstore.dto.ProductRequestDTO;
import com.techstore.dto.ProductResponseDTO;
import com.techstore.dto.ProductSpecificationRequestDTO;
import com.techstore.dto.ProductSummaryDTO;
import com.techstore.entity.Category;
import com.techstore.entity.CategorySpecificationTemplate;
import com.techstore.entity.Manufacturer;
import com.techstore.entity.Product;
import com.techstore.enums.ProductStatus;
import com.techstore.exception.BusinessLogicException;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.repository.CategoryRepository;
import com.techstore.repository.CategorySpecificationTemplateRepository;
import com.techstore.repository.ManufacturerRepository;
import com.techstore.repository.ProductRepository;
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
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CategorySpecificationTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;
    private final ManufacturerRepository manufacturerRepository;

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
    public Page<ProductSummaryDTO> getProductsByCategory(Long categoryId, Pageable pageable) {
        return productRepository.findByActiveTrueAndCategoryId(categoryId, pageable)
                .map(this::convertToSummaryDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryDTO> getProductsByBrand(Long brandId, Pageable pageable) {
        return productRepository.findByActiveTrueAndManufacturerId(brandId, pageable)
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
                                                  ProductStatus status, Boolean onSale, String query,
                                                  Pageable pageable) {
        return productRepository.findProductsWithFilters(categoryId, brandId, minPrice, maxPrice,
                        status, onSale, query, pageable)
                .map(this::convertToSummaryDTO);
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryDTO> getRelatedProducts(Long productId, int limit) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        Pageable pageable = Pageable.ofSize(limit);
        return productRepository.findRelatedProducts(productId, product.getCategory().getId(),
                        product.getManufacturer().getId(), pageable)
                .stream()
                .map(this::convertToSummaryDTO)
                .toList();
    }

    // ===== WRITE OPERATIONS =====

    public ProductResponseDTO createProduct(ProductRequestDTO requestDTO) {
        log.info("Creating product with specification validation: {}", requestDTO.getSku());

        // Get category templates for validation
        List<CategorySpecificationTemplate> requiredTemplates =
                templateRepository.findByCategoryIdAndRequiredTrueOrderBySortOrderAsc(requestDTO.getCategoryId());

        // Validate required specifications are provided
        validateRequiredSpecifications(requestDTO.getSpecifications(), requiredTemplates);

        // Create product
        Product product = convertToEntity(requestDTO);
        product = productRepository.save(product);

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

    public ProductResponseDTO updateProduct(Long id, ProductRequestDTO requestDTO) {
        log.info("Updating product with id: {}", id);

        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        updateProductFromDTO(existingProduct, requestDTO);
        Product updatedProduct = productRepository.save(existingProduct);

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
            return objectMapper.readValue(allowedValuesJson, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("Failed to parse allowed values: {}", allowedValuesJson);
            return Collections.emptyList();
        }
    }

    private Product convertToEntity(ProductRequestDTO dto) {
        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + dto.getCategoryId()));

        Manufacturer manufacturer = manufacturerRepository.findById(dto.getManufacturerId())
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + dto.getManufacturerId()));

        Product product = new Product();
        product.setNameEn(dto.getName());
        product.setDescriptionEn(dto.getDescriptionEn());
        product.setDescriptionBg(dto.getDescriptionBg());
        product.setPriceClient(dto.getPriceClient());
        product.setDiscount(dto.getDiscount());
        product.setActive(dto.getActive());
        product.setFeatured(dto.getFeatured());
        product.setPrimaryImageUrl(dto.getImageUrl());
        product.setAdditionalImages(dto.getAdditionalImages());
        product.setWarranty(Integer.parseInt(dto.getWarranty()));
        product.setWeight(dto.getWeight());
        product.setCategory(category);
        product.setManufacturer(manufacturer);

        return product;
    }

    private void updateProductFromDTO(Product product, ProductRequestDTO dto) {
        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + dto.getCategoryId()));

        Manufacturer manufacturer = manufacturerRepository.findById(dto.getManufacturerId())
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + dto.getManufacturerId()));

        product.setNameEn(dto.getName());
        product.setDescriptionEn(dto.getDescriptionEn());
        product.setDescriptionBg(dto.getDescriptionBg());
        product.setPriceClient(dto.getPriceClient());
        product.setDiscount(dto.getDiscount());
        product.setActive(dto.getActive());
        product.setFeatured(dto.getFeatured());
        product.setPrimaryImageUrl(dto.getImageUrl());
        product.setAdditionalImages(dto.getAdditionalImages());
        product.setWarranty(Integer.parseInt(dto.getWarranty()));
        product.setWeight(dto.getWeight());
        product.setCategory(category);
        product.setManufacturer(manufacturer);
    }

    private ProductResponseDTO convertToResponseDTO(Product product) {
        return ProductResponseDTO.builder()
                .id(product.getId())
                .name(product.getNameEn())
                .descriptionBg(product.getDescriptionBg())
                .descriptionEn(product.getDescriptionEn())
                .priceClient(product.getPriceClient())
                .pricePartner(product.getPricePartner())
                .discount(product.getDiscount())
                .pricePromo(product.getPricePromo())
                .active(product.getActive())
                .featured(product.getFeatured())
                .primaryImageUrl(product.getPrimaryImageUrl())
                .additionalImages(product.getAdditionalImages())
                .warranty(product.getWarranty())
                .weight(product.getWeight())
                .category(convertToCategorySummary(product.getCategory()))
                .manufacturer(convertToManufacturerSummary(product.getManufacturer()))
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .onSale(product.isOnSale())
                .status(product.getStatus().getCode())
                .build();
    }

    private ProductSummaryDTO convertToSummaryDTO(Product product) {
        return ProductSummaryDTO.builder()
                .id(product.getId())
                .name(product.getNameEn())
                .manufacturerName(product.getManufacturer().getName())
                .priceClient(product.getPriceClient())
                .pricePartner(product.getPricePartner())
                .pricePromo(product.getPricePromo())
                .priceClientPromo(product.getPriceClientPromo())
                .discount(product.getDiscount())
                .active(product.getActive())
                .featured(product.getFeatured())
                .primaryImageUrl(product.getPrimaryImageUrl())
                .categoryName(product.getCategory().getNameEn())
                .onSale(product.isOnSale())
                .status(product.getStatus().getCode())
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

    private ManufacturerSummaryDto convertToManufacturerSummary(Manufacturer brand) {
        return ManufacturerSummaryDto.builder()
                .id(brand.getId())
                .name(brand.getName())
                .build();
    }
}