package com.techstore.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductStatisticsDto {
    private Long totalProducts;
    private Long availableProducts;
    private Long categoriesWithProducts;
    private Long totalManufacturers;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal averagePrice;
}