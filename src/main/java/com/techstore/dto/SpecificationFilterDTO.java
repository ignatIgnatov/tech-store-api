package com.techstore.dto;

import com.techstore.entity.CategorySpecificationTemplate;
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
public class SpecificationFilterDTO {
    private Long templateId;
    private String specName;
    private String specGroup;
    private CategorySpecificationTemplate.SpecificationType type;
    private List<String> availableValues;
    private RangeDTO numericRange;
    private String unit;
    private String description;
    private Boolean required;
    private Integer totalProducts; // How many products have this spec

    // Value counts for dropdown filters
    private List<FilterOptionDTO> options;

    // For range filters
    private BigDecimal step;
    private String rangeLabel;

    // Helper methods
    public boolean hasOptions() {
        return options != null && !options.isEmpty();
    }

    public boolean hasNumericRange() {
        return numericRange != null && numericRange.isValid();
    }

    public boolean isDropdownType() {
        return type == CategorySpecificationTemplate.SpecificationType.DROPDOWN ||
                type == CategorySpecificationTemplate.SpecificationType.MULTI_SELECT;
    }

    public boolean isRangeType() {
        return type == CategorySpecificationTemplate.SpecificationType.NUMBER ||
                type == CategorySpecificationTemplate.SpecificationType.DECIMAL ||
                type == CategorySpecificationTemplate.SpecificationType.RANGE;
    }
}
