package com.techstore.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CartItemResponseDto {
    private Long id;
    private ProductSummaryDto product;
    private Integer quantity;
    private BigDecimal itemTotal;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
