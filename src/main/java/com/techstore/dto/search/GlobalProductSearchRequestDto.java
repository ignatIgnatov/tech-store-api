package com.techstore.dto.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalProductSearchRequestDto {

    @Size(max = 200, message = "Search query cannot exceed 200 characters")
    private String query;

    private String productName;
    private String description;
    private String referenceNumber;
    private String model;
    private String barcode;

    private Long categoryId;
    private List<Long> categoryIds;
    private Long manufacturerId;
    private List<Long> manufacturerIds;
    private String manufacturerName;

    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    private List<String> statuses;
    private Boolean onSale;
    private Boolean featured;
    private Boolean active;
    private Boolean inStock;

    private List<ParameterFilter> parameterFilters;

    private Integer minWarranty;
    private Integer maxWarranty;
    private BigDecimal minWeight;
    private BigDecimal maxWeight;
    private List<String> flags;

    private Boolean exactMatch;
    private Boolean fuzzySearch;
    private SearchMode searchMode;

    private String sortBy = "relevance"; // relevance, price, name, createdAt, popularity
    private String sortDirection = "desc"; // asc, desc

    @Min(0)
    private int page = 0;

    @Min(1)
    @Max(100)
    private int size = 20;

    private String language = "en";

    private Boolean includeInactive;
    private Boolean highlightResults;
    private Boolean facetedSearch; // Return facets/aggregations

    public enum SearchMode {
        ALL_FIELDS,      // Search across all indexed fields
        SPECIFIC_FIELDS, // Search only in specified fields
        SMART           // AI-like search with field weighting
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParameterFilter {
        private Long parameterId;
        private String parameterName;
        private List<Long> optionIds;
        private List<String> optionValues;
        private String operator = "OR"; // OR, AND
    }
}
