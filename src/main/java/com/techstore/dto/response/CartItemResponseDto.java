package com.techstore.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CartItemResponseDto {
    private Long id;
    private ProductSummaryDto product;
    private Integer quantity;
    private BigDecimal itemTotal;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
