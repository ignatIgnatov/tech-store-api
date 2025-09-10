package com.techstore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSpecificationRequestDTO {

    @NotBlank(message = "Specification name is required")
    @Size(max = 200, message = "Specification name must not exceed 200 characters")
    private String specName;

    @NotBlank(message = "Specification value is required")
    @Size(max = 1000, message = "Specification value must not exceed 1000 characters")
    private String specValue;

    @Size(max = 500, message = "Specification unit must not exceed 500 characters")
    private String specUnit;

    @Size(max = 100, message = "Specification group must not exceed 100 characters")
    private String specGroup;

    private Integer sortOrder = 0;
}