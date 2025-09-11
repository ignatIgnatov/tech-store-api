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
    private String specName; // Used to find the template

    @NotBlank(message = "Specification value is required")
    @Size(max = 1000, message = "Specification value must not exceed 1000 characters")
    private String specValue;

    // For range specifications (min-max values)
    @Size(max = 1000, message = "Secondary specification value must not exceed 1000 characters")
    private String specValueSecondary;
}