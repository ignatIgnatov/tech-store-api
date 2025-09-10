package com.techstore.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandRequestDTO {

    @NotBlank(message = "Brand name is required")
    @Size(max = 200, message = "Brand name must not exceed 200 characters")
    private String name;

    @NotBlank(message = "Brand slug is required")
    @Size(max = 200, message = "Brand slug must not exceed 200 characters")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase letters, numbers, and hyphens")
    private String slug;

    private String description;

    @Size(max = 1000, message = "Logo URL must not exceed 1000 characters")
    private String logoUrl;

    @Size(max = 1000, message = "Website URL must not exceed 1000 characters")
    private String websiteUrl;

    @Size(max = 100, message = "Country must not exceed 100 characters")
    private String country;

    private Boolean active = true;
    private Boolean featured = false;

    @Min(value = 0, message = "Sort order cannot be negative")
    private Integer sortOrder = 0;
}
