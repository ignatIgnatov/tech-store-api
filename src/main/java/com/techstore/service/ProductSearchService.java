package com.techstore.service;

import com.techstore.dto.request.ProductSearchRequest;
import com.techstore.dto.response.ProductSearchResponse;
import com.techstore.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ProductSearchService {

    private final ProductSearchRepository searchRepository;

    public ProductSearchResponse searchProducts(ProductSearchRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            log.debug("Searching products with query: '{}', language: {}", request.getQuery(), request.getLanguage());

            // Validate and sanitize input
            validateSearchRequest(request);

            // Perform search
            ProductSearchResponse response = searchRepository.searchProducts(request);

            // Set actual search time
            long searchTime = System.currentTimeMillis() - startTime;
            response.setSearchTime(searchTime);

            log.debug("Search completed in {}ms, found {} products",
                    searchTime, response.getTotalElements());

            return response;

        } catch (Exception e) {
            log.error("PostgreSQL search failed for query: '{}'", request.getQuery(), e);
            throw new RuntimeException("Search failed", e);
        }
    }

    public List<String> getSearchSuggestions(String query, String language, int maxSuggestions) {
        if (!StringUtils.hasText(query) || query.length() < 2) {
            return Collections.emptyList();
        }

        try {
            String sanitizedQuery = sanitizeQuery(query);
            if (sanitizedQuery.length() < 2) {
                return Collections.emptyList();
            }

            return searchRepository.getSearchSuggestions(sanitizedQuery, language, maxSuggestions);

        } catch (Exception e) {
            log.error("Failed to get search suggestions for query: '{}'", query, e);
            return Collections.emptyList();
        }
    }

    private void validateSearchRequest(ProductSearchRequest request) {
        if (request.getSize() > 100) {
            throw new IllegalArgumentException("Page size cannot exceed 100");
        }
        if (request.getPage() < 0) {
            throw new IllegalArgumentException("Page number cannot be negative");
        }
        if (request.getQuery() != null && request.getQuery().length() > 200) {
            throw new IllegalArgumentException("Search query too long");
        }
        if (request.getMinPrice() != null && request.getMaxPrice() != null &&
                request.getMinPrice().compareTo(request.getMaxPrice()) > 0) {
            throw new IllegalArgumentException("Minimum price cannot be greater than maximum price");
        }

        // Sanitize query
        if (StringUtils.hasText(request.getQuery())) {
            request.setQuery(sanitizeQuery(request.getQuery()));
        }
    }

    private String sanitizeQuery(String query) {
        if (!StringUtils.hasText(query)) {
            return "";
        }

        // Remove potentially dangerous characters for SQL
        String sanitized = query.replaceAll("[';\"\\\\]", " ");

        // Remove extra whitespace
        sanitized = sanitized.replaceAll("\\s+", " ").trim();

        // Limit length
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200);
        }

        return sanitized;
    }
}
