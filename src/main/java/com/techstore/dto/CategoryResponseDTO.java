package com.techstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponseDTO {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private String imageUrl;
    private Boolean active;
    private Integer sortOrder;
    private CategorySummaryDTO parent;
    private List<CategorySummaryDTO> children;
    private Integer productCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String fullPath;
    private Boolean isParentCategory;
    private Boolean hasChildren;
}
