package com.techstore.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CategoryResponseDto {
    private Long id;
    private Long externalId;
    private String name;
    private Long parentId;
    private String parentName;
    private List<CategoryResponseDto> children;
    private Boolean show;
    private Integer order;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}