package com.techstore.service;

import com.techstore.dto.response.CategorySummaryDTO;
import com.techstore.dto.response.ManufacturerSummaryDto;
import com.techstore.dto.ProductRequestDTO;
import com.techstore.dto.ProductResponseDTO;
import com.techstore.dto.ProductSummaryDTO;
import com.techstore.entity.Category;
import com.techstore.entity.Manufacturer;
import com.techstore.entity.Product;
import com.techstore.enums.ProductStatus;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.repository.CategoryRepository;
import com.techstore.repository.ManufacturerRepository;
import com.techstore.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ManufacturerRepository manufacturerRepository;

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

    public ProductResponseDTO createProduct(ProductRequestDTO requestDTO) {
        return null;
    }

    public ProductResponseDTO updateProduct(Long id, ProductRequestDTO requestDTO) {
        return null;
    }

    public void deleteProduct(Long id) {
        log.info("Deleting product with id: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

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
}