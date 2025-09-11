package com.techstore.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CategoryStatisticsDto {
    private Long categoryId;
    private String categoryName;
    private Long productCount;
    private BigDecimal averagePrice;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
}
