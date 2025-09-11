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
public class SpecificationFilterValueDTO {
    private Long templateId;
    private List<String> values;           // For dropdown/multi-select
    private String textValue;              // For text search
    private Boolean booleanValue;          // For boolean filters
    private BigDecimal minValue;           // For numeric ranges
    private BigDecimal maxValue;           // For numeric ranges
    private String operator;               // equals, contains, greater_than, less_than, between

    // Helper methods
    public boolean hasValues() {
        return values != null && !values.isEmpty();
    }

    public boolean hasTextValue() {
        return textValue != null && !textValue.trim().isEmpty();
    }

    public boolean hasBooleanValue() {
        return booleanValue != null;
    }

    public boolean hasNumericRange() {
        return minValue != null || maxValue != null;
    }

    public boolean isValid() {
        return hasValues() || hasTextValue() || hasBooleanValue() || hasNumericRange();
    }
}
