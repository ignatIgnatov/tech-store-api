package com.techstore.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductSearchRequestDto {
    private String query;
    private Long categoryId;
    private Long manufacturerId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private List<Long> parameterOptionIds;
    private String sortBy = "name"; // name, price, createdAt
    private String sortDirection = "asc"; // asc, desc

    @Min(value = 0, message = "Page must be non-negative")
    private Integer page = 0;

    @Min(value = 1, message = "Size must be at least 1")
    @Max(value = 100, message = "Size cannot exceed 100")
    private Integer size = 20;
}
