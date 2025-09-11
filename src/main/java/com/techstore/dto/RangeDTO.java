package com.techstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RangeDTO {
    private BigDecimal min;
    private BigDecimal max;
    private String unit;
    private String label;
    private Integer stepSize;
    private String formatPattern;

    // Helper methods
    public boolean isValid() {
        return min != null && max != null && min.compareTo(max) <= 0;
    }

    public String getFormattedRange() {
        if (!isValid()) return "Invalid range";

        String unitSuffix = unit != null ? " " + unit : "";
        return min + " - " + max + unitSuffix;
    }

    public boolean contains(BigDecimal value) {
        if (!isValid() || value == null) return false;
        return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }
}