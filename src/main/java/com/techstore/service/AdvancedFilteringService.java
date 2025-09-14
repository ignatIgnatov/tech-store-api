package com.techstore.service;

import com.techstore.dto.AdvancedFilterRequestDTO;
import com.techstore.dto.ProductSummaryDTO;
import com.techstore.entity.Product;
import com.techstore.repository.ProductRepository;
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
        if (filterRequest.getManufacturerId() != null) {
            candidates.retainAll(productRepository.findByActiveTrueAndManufacturerId(
                    filterRequest.getManufacturerId(), Pageable.unpaged()).getContent());
        }

        // Apply price range filter
        if (filterRequest.getMinPrice() != null || filterRequest.getMaxPrice() != null) {
            BigDecimal min = filterRequest.getMinPrice() != null ? filterRequest.getMinPrice() : BigDecimal.ZERO;
            BigDecimal max = filterRequest.getMaxPrice() != null ? filterRequest.getMaxPrice() : new BigDecimal("999999");
            candidates.retainAll(productRepository.findByPriceRange(min, max, Pageable.unpaged()).getContent());
        }

        // Apply text search
        if (filterRequest.getSearchQuery() != null && !filterRequest.getSearchQuery().trim().isEmpty()) {
            candidates.retainAll(productRepository.searchProducts(
                    filterRequest.getSearchQuery(), Pageable.unpaged()).getContent());
        }

        // Apply availability filter
        if (filterRequest.getInStockOnly() != null && filterRequest.getInStockOnly()) {
            candidates.removeIf(product -> product.getStatus().getCode() == 0);
        }

        // Apply sale filter
        if (filterRequest.getOnSaleOnly() != null && filterRequest.getOnSaleOnly()) {
            candidates.removeIf(product -> !product.isOnSale());
        }

        return new ArrayList<>(candidates);
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
}
