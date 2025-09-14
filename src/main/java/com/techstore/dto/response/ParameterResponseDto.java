package com.techstore.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
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
