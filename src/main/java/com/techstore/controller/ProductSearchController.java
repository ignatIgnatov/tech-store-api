package com.techstore.controller;

import com.techstore.dto.request.ProductSearchRequest;
import com.techstore.dto.response.ProductSearchResponse;
import com.techstore.service.ProductSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Slf4j
public class ProductSearchController {

    private final ProductSearchService searchService;

    @PostMapping("/search")
    public ResponseEntity<ProductSearchResponse> searchProducts(@RequestBody @Valid ProductSearchRequest request) {
        try {
            ProductSearchResponse response = searchService.searchProducts(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid search request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Search failed for query: '{}'", request.getQuery(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<ProductSearchResponse> searchProductsSimple(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "bg") String lang,
            @RequestParam(required = false) List<String> categories,
            @RequestParam(required = false) List<String> manufacturers,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "relevance") String sortBy,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {

        ProductSearchRequest request = ProductSearchRequest.builder()
                .query(q)
                .language(lang)
                .categories(categories)
                .manufacturers(manufacturers)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .sortBy(sortBy)
                .page(page)
                .size(size)
                .active(true)
                .build();

        return searchProducts(request);
    }

    @GetMapping("/suggestions")
    public ResponseEntity<List<String>> getSearchSuggestions(
            @RequestParam @NotBlank @Size(min = 2, max = 100) String q,
            @RequestParam(defaultValue = "bg") String lang,
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) int limit) {

        try {
            List<String> suggestions = searchService.getSearchSuggestions(q, lang, limit);
            return ResponseEntity.ok(suggestions);
        } catch (Exception e) {
            log.error("Failed to get suggestions for query: '{}'", q, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
