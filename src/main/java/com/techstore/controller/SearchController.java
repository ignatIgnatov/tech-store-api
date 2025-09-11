package com.techstore.controller;

import com.techstore.dto.ProductSummaryDTO;
import com.techstore.dto.SearchStatsDTO;
import com.techstore.service.EnhancedSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class SearchController {

    private final EnhancedSearchService searchService;

    @GetMapping
    public ResponseEntity<Page<ProductSummaryDTO>> search(
            @RequestParam String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<ProductSummaryDTO> results = searchService.searchWithSpecifications(q, categoryId, pageable);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/suggestions")
    public ResponseEntity<List<String>> getSuggestions(
            @RequestParam String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "10") int limit) {

        List<String> suggestions = searchService.getSearchSuggestions(q, categoryId, limit);
        return ResponseEntity.ok(suggestions);
    }

    @GetMapping("/stats")
    public ResponseEntity<SearchStatsDTO> getSearchStats(@RequestParam String q) {
        SearchStatsDTO stats = searchService.getSearchStatistics(q);
        return ResponseEntity.ok(stats);
    }
}
