package com.techstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSummaryDTO {
    private Long id;
    private String name;
    private String sku;
    private BigDecimal price;
    private BigDecimal discount;
    private BigDecimal discountedPrice;
    private Integer stockQuantity;
    private Boolean active;
    private Boolean featured;
    private String imageUrl;
    private String categoryName;
    private String brandName;
    private Boolean inStock;
    private Boolean onSale;
}
