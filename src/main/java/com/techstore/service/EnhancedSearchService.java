package com.techstore.service;

import com.techstore.dto.ProductSummaryDTO;
import com.techstore.dto.SearchStatsDTO;
import com.techstore.entity.Product;
import com.techstore.entity.ProductSpecification;
import com.techstore.repository.ProductRepository;
import com.techstore.repository.ProductSpecificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedSearchService {

    private final ProductRepository productRepository;
    private final ProductSpecificationRepository specificationRepository;

    /**
     * Enhanced search that looks in product fields AND specifications
     */
    public Page<ProductSummaryDTO> searchWithSpecifications(String query, Long categoryId, Pageable pageable) {
        Set<Product> results = new HashSet<>();

        // 1. Search in product names, descriptions, SKUs
        Page<Product> productMatches = productRepository.searchProducts(query, Pageable.unpaged());
        results.addAll(productMatches.getContent());

        // 2. Search in specifications - NOW THIS METHOD EXISTS!
        List<ProductSpecification> specMatches = specificationRepository.searchSpecifications(query);
        results.addAll(specMatches.stream()
                .map(ProductSpecification::getProduct)
                .filter(Product::getActive)
                .collect(Collectors.toList()));

        // 3. Search by specification value directly
        List<Product> specValueMatches = specificationRepository.findProductsBySpecificationValue(query);
        results.addAll(specValueMatches);

        // 4. Filter by category if specified
        if (categoryId != null) {
            results.removeIf(product -> !product.getCategory().getId().equals(categoryId));
        }

        // 5. Convert to pageable result
        List<Product> productList = new ArrayList<>(results);

        // Sort by relevance (products with name matches first, then spec matches)
        productList.sort((p1, p2) -> {
            boolean p1NameMatch = p1.getName().toLowerCase().contains(query.toLowerCase());
            boolean p2NameMatch = p2.getName().toLowerCase().contains(query.toLowerCase());

            if (p1NameMatch && !p2NameMatch) return -1;
            if (!p1NameMatch && p2NameMatch) return 1;
            return p1.getName().compareToIgnoreCase(p2.getName());
        });

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), productList.size());

        List<Product> pageContent = productList.subList(start, end);
        Page<Product> productPage = new PageImpl<>(pageContent, pageable, productList.size());

        return productPage.map(this::convertToSummaryDTO);
    }

    /**
     * Search suggestions based on specifications
     */
    public List<String> getSearchSuggestions(String query, Long categoryId, int limit) {
        Set<String> suggestions = new HashSet<>();

        // Get specification name suggestions
        List<ProductSpecification> specMatches = categoryId != null
                ? specificationRepository.searchSpecificationsByCategory(categoryId, query)
                : specificationRepository.searchSpecifications(query);

        specMatches.stream()
                .limit(limit / 2)
                .forEach(spec -> {
                    suggestions.add(spec.getTemplate().getSpecName());
                    suggestions.add(spec.getSpecValue());
                });

        // Get product name suggestions
        Page<Product> productMatches = productRepository.searchProducts(query, PageRequest.of(0, limit / 2));
        productMatches.getContent().forEach(product -> {
            suggestions.add(product.getName());
            suggestions.add(product.getBrand().getName());
        });

        return suggestions.stream()
                .filter(s -> s.toLowerCase().contains(query.toLowerCase()))
                .sorted()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get search statistics for analytics
     */
    public SearchStatsDTO getSearchStatistics(String query) {
        List<Object[]> specStats = specificationRepository.getSpecificationSearchStatistics(query);

        Map<String, Integer> specificationCounts = specStats.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).intValue()
                ));

        long totalProducts = productRepository.searchProducts(query, Pageable.unpaged()).getTotalElements();

        return SearchStatsDTO.builder()
                .totalProducts((int) totalProducts)
                .specificationMatches(specificationCounts)
                .searchQuery(query)
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
                .categoryName(product.getCategory().getNameEn())
                .brandName(product.getBrand().getName())
                .inStock(product.isInStock())
                .onSale(product.isOnSale())
                .build();
    }
}
