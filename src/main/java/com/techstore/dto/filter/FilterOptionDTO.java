package com.techstore.dto.filter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterOptionDTO {
    private String value;
    private String label;
    private Integer count;
    private Boolean selected;
    private Boolean disabled;
    private String description;

    // For grouped options
    private String group;
    private Integer sortOrder;

    // Helper methods
    public boolean isAvailable() {
        return count != null && count > 0 && !Boolean.TRUE.equals(disabled);
    }

    public String getDisplayLabel() {
        if (count != null && count > 0) {
            return label + " (" + count + ")";
        }
        return label;
    }
}
