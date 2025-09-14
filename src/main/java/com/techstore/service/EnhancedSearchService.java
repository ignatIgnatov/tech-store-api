package com.techstore.service;

import com.techstore.entity.Product;
import com.techstore.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedSearchService {

    private final ProductRepository productRepository;

    /**
     * Search suggestions based on specifications
     */
    public List<String> getSearchSuggestions(String query, Long categoryId, int limit) {
        Set<String> suggestions = new HashSet<>();

        // Get product name suggestions
        Page<Product> productMatches = productRepository.searchProducts(query, PageRequest.of(0, limit / 2));
        productMatches.getContent().forEach(product -> {
            suggestions.add(product.getNameEn());
            suggestions.add(product.getManufacturer().getName());
        });

        return suggestions.stream()
                .filter(s -> s.toLowerCase().contains(query.toLowerCase()))
                .sorted()
                .limit(limit)
                .collect(Collectors.toList());
    }
}
