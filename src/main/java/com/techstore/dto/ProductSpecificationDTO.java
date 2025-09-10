package com.techstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSpecificationDTO {
    private Long id;
    private String specName;
    private String specValue;
    private String specUnit;
    private String specGroup;
    private Integer sortOrder;
    private String formattedValue;
}
