package com.techstore.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CartSummaryDto {
    private List<CartItemResponseDto> items;
    private Integer totalItems;
    private BigDecimal totalPrice;
}
