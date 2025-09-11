package com.techstore.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ParameterOptionResponseDto {
    private Long id;
    private Long externalId;
    private String name;
    private Long parameterId;
    private String parameterName;
    private Integer order;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
