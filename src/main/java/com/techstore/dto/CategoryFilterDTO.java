package com.techstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryFilterDTO {
    private Long categoryId;
    private String categoryName;
    private List<SpecificationFilterDTO> filters;
    private PriceRangeDTO priceRange;
    private List<BrandSummaryDTO> availableBrands;

    // Additional filter options
    private List<FilterOptionDTO> availabilityOptions;
    private List<FilterOptionDTO> conditionOptions;
    private List<FilterOptionDTO> ratingOptions;

    // Grouping information
    private Map<String, List<SpecificationFilterDTO>> groupedFilters;
    private List<String> filterGroups;

    // Statistics
    private Integer totalProducts;
    private Integer filteredProducts;

    // Helper methods
    public Map<String, List<SpecificationFilterDTO>> getGroupedFilters() {
        if (groupedFilters == null && filters != null) {
            groupedFilters = filters.stream()
                    .filter(filter -> filter.getSpecGroup() != null)
                    .collect(Collectors.groupingBy(
                            SpecificationFilterDTO::getSpecGroup,
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));
        }
        return groupedFilters;
    }

    public List<String> getFilterGroups() {
        if (filterGroups == null) {
            filterGroups = new ArrayList<>(getGroupedFilters().keySet());
        }
        return filterGroups;
    }

    public boolean hasFilters() {
        return filters != null && !filters.isEmpty();
    }

    public boolean hasPriceRange() {
        return priceRange != null && priceRange.isValid();
    }
}
