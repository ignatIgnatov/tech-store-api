package com.techstore.dto;

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
public class AdvancedFilterRequestDTO {
    private Long categoryId;
    private Long manufacturerId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Boolean inStockOnly;
    private Boolean onSaleOnly;
    private Boolean featuredOnly;
    private String searchQuery;
    private List<SpecificationFilterValueDTO> specificationFilters;
    private String sortBy;
    private String sortDirection;

    // Additional filters
    private List<String> brands;
    private List<String> colors;
    private String condition;
    private Integer minRating;

    // Helper methods
    public boolean hasSpecificationFilters() {
        return specificationFilters != null && !specificationFilters.isEmpty();
    }

    public boolean hasPriceFilter() {
        return minPrice != null || maxPrice != null;
    }

    public boolean hasTextSearch() {
        return searchQuery != null && !searchQuery.trim().isEmpty();
    }
}
