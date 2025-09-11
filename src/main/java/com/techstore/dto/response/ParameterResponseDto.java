package com.techstore.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ParameterResponseDto {
    private Long id;
    private Long externalId;
    private String name;
    private Long categoryId;
    private String categoryName;
    private Integer order;
    private List<ParameterOptionResponseDto> options;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
