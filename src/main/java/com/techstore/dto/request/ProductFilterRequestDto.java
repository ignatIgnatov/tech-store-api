package com.techstore.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductFilterRequestDto {
    private List<Long> categoryIds;
    private List<Long> manufacturerIds;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private List<ProductParameterFilterDto> parameters;
    private List<String> statuses;
    private Boolean inStock;
}