package com.techstore.controller;

import com.techstore.dto.response.ProductSummaryDto;
import com.techstore.dto.search.GlobalProductSearchRequestDto;
import com.techstore.dto.search.GlobalSearchResponseDto;
import com.techstore.dto.search.SearchSuggestionDto;
import com.techstore.service.SearchService;
import com.techstore.util.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Search", description = "Global product search operations")
public class GlobalSearchController {

    private final SearchService searchService;
    private final SecurityHelper securityHelper;

    @Operation(
            summary = "Global product search",
            description = "Comprehensive search across all product fields with advanced filtering, faceting, and suggestions"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid search parameters"),
            @ApiResponse(responseCode = "500", description = "Search service error")
    })
    @PostMapping("/global")
    public ResponseEntity<GlobalSearchResponseDto> globalSearch(
            @Valid @RequestBody GlobalProductSearchRequestDto request) {

        log.info("Global search request: query='{}', page={}, size={}",
                request.getQuery(), request.getPage(), request.getSize());

        Long userId = securityHelper.getCurrentUserId();
        GlobalSearchResponseDto response = searchService.globalSearch(request, userId);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Quick search",
            description = "Simple text-based search with basic parameters"
    )
    @GetMapping("/quick")
    public ResponseEntity<Page<ProductSummaryDto>> quickSearch(
            @Parameter(description = "Search query", example = "samsung phone")
            @RequestParam(required = false)
            @Size(max = 200, message = "Query cannot exceed 200 characters")
            String q,

            @Parameter(description = "Category ID filter")
            @RequestParam(required = false) Long categoryId,

            @Parameter(description = "Manufacturer ID filter")
            @RequestParam(required = false) Long manufacturerId,

            @Parameter(description = "Minimum price filter")
            @RequestParam(required = false) BigDecimal minPrice,

            @Parameter(description = "Maximum price filter")
            @RequestParam(required = false) BigDecimal maxPrice,

            @Parameter(description = "Show only featured products")
            @RequestParam(required = false) Boolean featured,

            @Parameter(description = "Show only products on sale")
            @RequestParam(required = false) Boolean onSale,

            @Parameter(description = "Sort by field", example = "relevance")
            @RequestParam(defaultValue = "relevance") String sortBy,

            @Parameter(description = "Sort direction", example = "desc")
            @RequestParam(defaultValue = "desc") String sortDirection,

            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0")
            @Min(0) int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int size,

            @Parameter(description = "Language for results", example = "en")
            @RequestParam(defaultValue = "en") String lang) {

        log.debug("Quick search: q='{}', categoryId={}, manufacturerId={}", q, categoryId, manufacturerId);

        GlobalProductSearchRequestDto request = GlobalProductSearchRequestDto.builder()
                .query(q)
                .categoryId(categoryId)
                .manufacturerId(manufacturerId)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .featured(featured)
                .onSale(onSale)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .page(page)
                .size(size)
                .language(lang)
                .searchMode(GlobalProductSearchRequestDto.SearchMode.SMART)
                .build();

        Long userId = securityHelper.getCurrentUserId();
        Page<ProductSummaryDto> results = searchService.advancedSearch(request, userId);

        return ResponseEntity.ok(results);
    }

    @Operation(
            summary = "Search suggestions",
            description = "Get autocomplete suggestions for search queries"
    )
    @GetMapping("/suggestions")
    public ResponseEntity<SearchSuggestionDto> getSearchSuggestions(
            @Parameter(description = "Partial search query", example = "samsu")
            @RequestParam
            @Size(min = 2, max = 50, message = "Query must be between 2 and 50 characters")
            String q,

            @Parameter(description = "Language for suggestions", example = "en")
            @RequestParam(defaultValue = "en") String lang,

            @Parameter(description = "Maximum number of suggestions")
            @RequestParam(defaultValue = "10")
            @Min(1) @Max(50) int limit) {

        log.debug("Getting suggestions for query: '{}'", q);

        SearchSuggestionDto suggestions = searchService.getSearchSuggestions(q, lang, limit);
        return ResponseEntity.ok(suggestions);
    }

    @Operation(
            summary = "Advanced search",
            description = "Detailed search with multiple criteria and parameter filtering"
    )
    @PostMapping("/advanced")
    public ResponseEntity<Page<ProductSummaryDto>> advancedSearch(
            @Valid @RequestBody GlobalProductSearchRequestDto request) {

        log.info("Advanced search with {} parameter filters",
                request.getParameterFilters() != null ? request.getParameterFilters().size() : 0);

        Long userId = securityHelper.getCurrentUserId();
        Page<ProductSummaryDto> results = searchService.advancedSearch(request, userId);

        return ResponseEntity.ok(results);
    }

    @Operation(
            summary = "Search by reference number",
            description = "Find products by exact or partial reference number match"
    )
    @GetMapping("/reference")
    public ResponseEntity<Page<ProductSummaryDto>> searchByReference(
            @Parameter(description = "Reference number", example = "ABC123")
            @RequestParam
            @Size(min = 3, max = 100, message = "Reference must be between 3 and 100 characters")
            String reference,

            @Parameter(description = "Exact match vs partial match")
            @RequestParam(defaultValue = "false") boolean exact,

            @Parameter(description = "Page number")
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,

            @Parameter(description = "Language")
            @RequestParam(defaultValue = "en") String lang) {

        log.debug("Searching by reference: '{}' (exact: {})", reference, exact);

        GlobalProductSearchRequestDto request = GlobalProductSearchRequestDto.builder()
                .referenceNumber(reference)
                .exactMatch(exact)
                .page(page)
                .size(size)
                .language(lang)
                .searchMode(GlobalProductSearchRequestDto.SearchMode.SPECIFIC_FIELDS)
                .build();

        Long userId = securityHelper.getCurrentUserId();
        Page<ProductSummaryDto> results = searchService.advancedSearch(request, userId);

        return ResponseEntity.ok(results);
    }

    @Operation(
            summary = "Search by barcode",
            description = "Find products by barcode"
    )
    @GetMapping("/barcode")
    public ResponseEntity<Page<ProductSummaryDto>> searchByBarcode(
            @Parameter(description = "Product barcode", example = "1234567890123")
            @RequestParam
            @Size(min = 5, max = 50, message = "Barcode must be between 5 and 50 characters")
            String barcode,

            @Parameter(description = "Language")
            @RequestParam(defaultValue = "en") String lang) {

        log.debug("Searching by barcode: '{}'", barcode);

        GlobalProductSearchRequestDto request = GlobalProductSearchRequestDto.builder()
                .barcode(barcode)
                .exactMatch(true)
                .page(0)
                .size(10)
                .language(lang)
                .searchMode(GlobalProductSearchRequestDto.SearchMode.SPECIFIC_FIELDS)
                .build();

        Long userId = securityHelper.getCurrentUserId();
        Page<ProductSummaryDto> results = searchService.advancedSearch(request, userId);

        return ResponseEntity.ok(results);
    }

    @Operation(
            summary = "Search by manufacturer",
            description = "Find all products from specific manufacturers"
    )
    @GetMapping("/manufacturer")
    public ResponseEntity<Page<ProductSummaryDto>> searchByManufacturer(
            @Parameter(description = "Manufacturer ID or name")
            @RequestParam(required = false) Long manufacturerId,

            @Parameter(description = "Manufacturer name")
            @RequestParam(required = false) String manufacturerName,

            @Parameter(description = "Additional search query")
            @RequestParam(required = false) String q,

            @Parameter(description = "Category filter")
            @RequestParam(required = false) Long categoryId,

            @Parameter(description = "Price range")
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,

            @Parameter(description = "Sort by")
            @RequestParam(defaultValue = "name") String sortBy,

            @Parameter(description = "Sort direction")
            @RequestParam(defaultValue = "asc") String sortDirection,

            @Parameter(description = "Page number")
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,

            @Parameter(description = "Language")
            @RequestParam(defaultValue = "en") String lang) {

        log.debug("Searching by manufacturer: ID={}, name='{}'", manufacturerId, manufacturerName);

        GlobalProductSearchRequestDto request = GlobalProductSearchRequestDto.builder()
                .query(q)
                .manufacturerId(manufacturerId)
                .manufacturerName(manufacturerName)
                .categoryId(categoryId)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .page(page)
                .size(size)
                .language(lang)
                .build();

        Long userId = securityHelper.getCurrentUserId();
        Page<ProductSummaryDto> results = searchService.advancedSearch(request, userId);

        return ResponseEntity.ok(results);
    }

    @Operation(
            summary = "Search by category",
            description = "Find all products in specific categories with optional filtering"
    )
    @GetMapping("/category")
    public ResponseEntity<Page<ProductSummaryDto>> searchByCategory(
            @Parameter(description = "Category ID", required = true)
            @RequestParam Long categoryId,

            @Parameter(description = "Additional search query")
            @RequestParam(required = false) String q,

            @Parameter(description = "Manufacturer filter")
            @RequestParam(required = false) Long manufacturerId,

            @Parameter(description = "Price range")
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,

            @Parameter(description = "Show only featured")
            @RequestParam(required = false) Boolean featured,

            @Parameter(description = "Show only on sale")
            @RequestParam(required = false) Boolean onSale,

            @Parameter(description = "Sort by")
            @RequestParam(defaultValue = "relevance") String sortBy,

            @Parameter(description = "Sort direction")
            @RequestParam(defaultValue = "desc") String sortDirection,

            @Parameter(description = "Page number")
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,

            @Parameter(description = "Language")
            @RequestParam(defaultValue = "en") String lang) {

        log.debug("Searching by category: {}, query: '{}'", categoryId, q);

        GlobalProductSearchRequestDto request = GlobalProductSearchRequestDto.builder()
                .query(q)
                .categoryId(categoryId)
                .manufacturerId(manufacturerId)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .featured(featured)
                .onSale(onSale)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .page(page)
                .size(size)
                .language(lang)
                .build();

        Long userId = securityHelper.getCurrentUserId();
        Page<ProductSummaryDto> results = searchService.advancedSearch(request, userId);

        return ResponseEntity.ok(results);
    }

    @Operation(
            summary = "Search with parameters",
            description = "Find products with specific parameter values/specifications"
    )
    @PostMapping("/parameters")
    public ResponseEntity<Page<ProductSummaryDto>> searchByParameters(
            @Parameter(description = "Parameter filter specifications")
            @RequestBody @Valid List<GlobalProductSearchRequestDto.ParameterFilter> parameterFilters,

            @Parameter(description = "Additional search query")
            @RequestParam(required = false) String q,

            @Parameter(description = "Category filter")
            @RequestParam(required = false) Long categoryId,

            @Parameter(description = "Page number")
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,

            @Parameter(description = "Language")
            @RequestParam(defaultValue = "en") String lang) {

        log.debug("Searching by parameters: {} filters", parameterFilters.size());

        GlobalProductSearchRequestDto request = GlobalProductSearchRequestDto.builder()
                .query(q)
                .categoryId(categoryId)
                .parameterFilters(parameterFilters)
                .page(page)
                .size(size)
                .language(lang)
                .build();

        Long userId = securityHelper.getCurrentUserId();
        Page<ProductSummaryDto> results = searchService.advancedSearch(request, userId);

        return ResponseEntity.ok(results);
    }

    @Operation(
            summary = "Search trending/popular",
            description = "Get trending or popular products based on search patterns"
    )
    @GetMapping("/trending")
    public ResponseEntity<Page<ProductSummaryDto>> getTrendingProducts(
            @Parameter(description = "Category filter")
            @RequestParam(required = false) Long categoryId,

            @Parameter(description = "Time period for trending")
            @RequestParam(defaultValue = "week") String period, // day, week, month

            @Parameter(description = "Page number")
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,

            @Parameter(description = "Language")
            @RequestParam(defaultValue = "en") String lang) {

        log.debug("Getting trending products for period: {}", period);

        // For now, return featured products as trending
        // In a full implementation, you would track search/view patterns
        GlobalProductSearchRequestDto request = GlobalProductSearchRequestDto.builder()
                .categoryId(categoryId)
                .featured(true)
                .sortBy("popularity")
                .sortDirection("desc")
                .page(page)
                .size(size)
                .language(lang)
                .build();

        Long userId = securityHelper.getCurrentUserId();
        Page<ProductSummaryDto> results = searchService.advancedSearch(request, userId);

        return ResponseEntity.ok(results);
    }
}