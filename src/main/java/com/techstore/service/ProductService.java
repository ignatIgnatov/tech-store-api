package com.techstore.service;

import com.techstore.dto.*;
import com.techstore.entity.Product;
import com.techstore.entity.ProductSpecification;
import com.techstore.entity.Category;
import com.techstore.entity.Brand;
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
import java.util.List;
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
        log.info("Creating new product with SKU: {}", requestDTO.getSku());

        // Validate SKU uniqueness
        if (productRepository.existsBySku(requestDTO.getSku())) {
            throw new DuplicateResourceException("Product with SKU '" + requestDTO.getSku() + "' already exists");
        }

        Product product = convertToEntity(requestDTO);
        product = productRepository.save(product);

        // Save specifications
        if (requestDTO.getSpecifications() != null) {
            saveProductSpecifications(product, requestDTO.getSpecifications());
        }

        log.info("Product created successfully with id: {}", product.getId());
        return convertToResponseDTO(product);
    }

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

    private void saveProductSpecifications(Product product, List<ProductSpecificationRequestDTO> specDTOs) {
        List<ProductSpecification> specifications = specDTOs.stream()
                .map(specDTO -> {
                    ProductSpecification spec = new ProductSpecification();
                    spec.setSpecName(specDTO.getSpecName());
                    spec.setSpecValue(specDTO.getSpecValue());
                    spec.setSpecUnit(specDTO.getSpecUnit());
                    spec.setSpecGroup(specDTO.getSpecGroup());
                    spec.setSortOrder(specDTO.getSortOrder());
                    spec.setProduct(product);
                    return spec;
                })
                .collect(Collectors.toList());

        specificationRepository.saveAll(specifications);
    }

    private Product convertToEntity(ProductRequestDTO dto) {
        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + dto.getCategoryId()));

        Brand brand = brandRepository.findById(dto.getBrandId())
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + dto.getBrandId()));

        Product product = new Product();
        product.setName(dto.getName());
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

        product.setName(dto.getName());
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
                .name(product.getName())
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
                .name(product.getName())
                .sku(product.getSku())
                .price(product.getPrice())
                .discount(product.getDiscount())
                .discountedPrice(product.getDiscountedPrice())
                .stockQuantity(product.getStockQuantity())
                .active(product.getActive())
                .featured(product.getFeatured())
                .imageUrl(product.getImageUrl())
                .categoryName(product.getCategory().getName())
                .brandName(product.getBrand().getName())
                .inStock(product.isInStock())
                .onSale(product.isOnSale())
                .build();
    }

    private CategorySummaryDTO convertToCategorySummary(Category category) {
        return CategorySummaryDTO.builder()
                .id(category.getId())
                .name(category.getName())
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