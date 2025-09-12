package com.techstore.service;

import com.techstore.dto.AdvancedFilterRequestDTO;
import com.techstore.dto.ProductSummaryDTO;
import com.techstore.dto.SpecificationFilterValueDTO;
import com.techstore.entity.CategorySpecificationTemplate;
import com.techstore.entity.Product;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.repository.CategorySpecificationTemplateRepository;
import com.techstore.repository.ProductRepository;
import com.techstore.repository.ProductSpecificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AdvancedFilteringService {

    private final ProductRepository productRepository;
    private final ProductSpecificationRepository specificationRepository;
    private final CategorySpecificationTemplateRepository templateRepository;

    /**
     * Advanced product filtering with specification-based filters
     */
    public Page<ProductSummaryDTO> filterProductsAdvanced(AdvancedFilterRequestDTO filterRequest, Pageable pageable) {
        // Build dynamic query based on filters
        List<Product> filteredProducts = buildFilteredProductQuery(filterRequest);

        // Convert to pageable result
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filteredProducts.size());

        List<Product> pageContent = filteredProducts.subList(start, end);

        Page<Product> productPage = new PageImpl<>(pageContent, pageable, filteredProducts.size());

        return productPage.map(this::convertToSummaryDTO);
    }

    private List<Product> buildFilteredProductQuery(AdvancedFilterRequestDTO filterRequest) {
        // Start with base query
        Set<Product> candidates = new HashSet<>(productRepository.findByActiveTrueOrderByNameEnAsc());

        // Apply category filter
        if (filterRequest.getCategoryId() != null) {
            candidates.retainAll(productRepository.findByActiveTrueAndCategoryId(
                    filterRequest.getCategoryId(), Pageable.unpaged()).getContent());
        }

        // Apply brand filter
        if (filterRequest.getBrandId() != null) {
            candidates.retainAll(productRepository.findByActiveTrueAndBrandId(
                    filterRequest.getBrandId(), Pageable.unpaged()).getContent());
        }

        // Apply price range filter
        if (filterRequest.getMinPrice() != null || filterRequest.getMaxPrice() != null) {
            BigDecimal min = filterRequest.getMinPrice() != null ? filterRequest.getMinPrice() : BigDecimal.ZERO;
            BigDecimal max = filterRequest.getMaxPrice() != null ? filterRequest.getMaxPrice() : new BigDecimal("999999");
            candidates.retainAll(productRepository.findByPriceRange(min, max, Pageable.unpaged()).getContent());
        }

        // Apply specification filters
        if (filterRequest.getSpecificationFilters() != null) {
            for (SpecificationFilterValueDTO specFilter : filterRequest.getSpecificationFilters()) {
                Set<Product> specMatches = filterBySpecification(specFilter);
                candidates.retainAll(specMatches);
            }
        }

        // Apply text search
        if (filterRequest.getSearchQuery() != null && !filterRequest.getSearchQuery().trim().isEmpty()) {
            candidates.retainAll(productRepository.searchProducts(
                    filterRequest.getSearchQuery(), Pageable.unpaged()).getContent());
        }

        // Apply availability filter
        if (filterRequest.getInStockOnly() != null && filterRequest.getInStockOnly()) {
            candidates.removeIf(product -> product.getStockQuantity() <= 0);
        }

        // Apply sale filter
        if (filterRequest.getOnSaleOnly() != null && filterRequest.getOnSaleOnly()) {
            candidates.removeIf(product -> !product.isOnSale());
        }

        return new ArrayList<>(candidates);
    }

    private Set<Product> filterBySpecification(SpecificationFilterValueDTO specFilter) {
        CategorySpecificationTemplate template = templateRepository.findById(specFilter.getTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("Template not found"));

        switch (template.getType()) {
            case NUMBER:
            case DECIMAL:
                return filterByNumericRange(specFilter);
            case DROPDOWN:
            case MULTI_SELECT:
                return filterByExactValues(specFilter);
            case BOOLEAN:
                return filterByBooleanValue(specFilter);
            case RANGE:
                return filterByRange(specFilter);
            default:
                return filterByTextValue(specFilter);
        }
    }

    private Set<Product> filterByNumericRange(SpecificationFilterValueDTO specFilter) {
        if (specFilter.getMinValue() != null || specFilter.getMaxValue() != null) {
            BigDecimal min = specFilter.getMinValue() != null ? specFilter.getMinValue() : new BigDecimal("-999999");
            BigDecimal max = specFilter.getMaxValue() != null ? specFilter.getMaxValue() : new BigDecimal("999999");

            return new HashSet<>(specificationRepository.findProductsByNumericRange(
                    specFilter.getTemplateId(), min, max));
        }
        return new HashSet<>();
    }

    private Set<Product> filterByExactValues(SpecificationFilterValueDTO specFilter) {
        if (specFilter.getValues() != null && !specFilter.getValues().isEmpty()) {
            Set<Product> matches = new HashSet<>();
            for (String value : specFilter.getValues()) {
                matches.addAll(specificationRepository.findProductsBySpecification(
                        specFilter.getTemplateId(), value));
            }
            return matches;
        }
        return new HashSet<>();
    }

    private Set<Product> filterByBooleanValue(SpecificationFilterValueDTO specFilter) {
        if (specFilter.getBooleanValue() != null) {
            String value = specFilter.getBooleanValue() ? "true" : "false";
            return new HashSet<>(specificationRepository.findProductsBySpecification(
                    specFilter.getTemplateId(), value));
        }
        return new HashSet<>();
    }

    private Set<Product> filterByRange(SpecificationFilterValueDTO specFilter) {
        // For range specifications (like "100-200"), we need custom logic
        return new HashSet<>(); // Implementation depends on your range format
    }

    private Set<Product> filterByTextValue(SpecificationFilterValueDTO specFilter) {
        if (specFilter.getTextValue() != null) {
            return new HashSet<>(specificationRepository.findProductsBySpecification(
                    specFilter.getTemplateId(), specFilter.getTextValue()));
        }
        return new HashSet<>();
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
}
