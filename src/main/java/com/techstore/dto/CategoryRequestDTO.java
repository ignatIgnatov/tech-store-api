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
public class CategoryRequestDTO {

    @NotBlank(message = "Category name is required")
    @Size(max = 200, message = "Category name must not exceed 200 characters")
    private String name;

    @NotBlank(message = "Category slug is required")
    @Size(max = 200, message = "Category slug must not exceed 200 characters")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase letters, numbers, and hyphens")
    private String slug;

    private String description;

    @Size(max = 1000, message = "Image URL must not exceed 1000 characters")
    private String imageUrl;

    private Boolean active = true;

    @Min(value = 0, message = "Sort order cannot be negative")
    private Integer sortOrder = 0;

    private Long parentId;
}
