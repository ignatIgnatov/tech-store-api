package com.techstore.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserFavoriteResponseDto {
    private Long id;
    private ProductSummaryDto product;
    private LocalDateTime createdAt;
}
