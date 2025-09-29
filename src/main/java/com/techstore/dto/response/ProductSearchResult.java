package com.techstore.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchResult {
    private Long id;
    private String name;
    private String description;
    private String model;
    private String referenceNumber;
    private BigDecimal finalPrice;
    private BigDecimal discount;
    private String primaryImageUrl;
    private String manufacturerName;
    private String categoryName;
    private Boolean featured;
    private Boolean onSale;
    private Float score; // Search relevance score
}
