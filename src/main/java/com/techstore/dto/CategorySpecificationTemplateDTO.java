package com.techstore.dto;

import com.techstore.entity.CategorySpecificationTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategorySpecificationTemplateDTO {
    private Long id;
    private String specName;
    private String specUnit;
    private String specGroup;
    private Boolean required;
    private Boolean filterable;
    private Boolean searchable;
    private Integer sortOrder;
    private CategorySpecificationTemplate.SpecificationType type;
    private List<String> allowedValues;
    private String description;
    private String placeholder;
    private Boolean showInListing;
    private Boolean showInComparison;
    private Long categoryId;
}
