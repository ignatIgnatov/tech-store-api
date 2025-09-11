package com.techstore.dto;

import com.techstore.entity.CategorySpecificationTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSpecificationEnhancedDTO {
    private Long id;
    private String specValue;
    private String specValueSecondary;
    private Integer sortOrder;
    private Long templateId;
    private String specName;
    private String specUnit;
    private String specGroup;
    private CategorySpecificationTemplate.SpecificationType type;
    private String formattedValue;
    private Boolean showInListing;
    private Boolean showInComparison;
}
