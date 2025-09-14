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
    private Long externalId;
    private String nameBg;
    private String nameEn;
    private String slug;
    private Integer sortOrder;
    private CategorySummaryDTO parent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isParentCategory;
    private Boolean show;
}
