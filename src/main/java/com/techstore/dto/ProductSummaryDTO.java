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
    private String manufacturerName;
    private BigDecimal priceClient;
    private BigDecimal pricePartner;
    private BigDecimal pricePromo;
    private BigDecimal priceClientPromo;
    private BigDecimal discount;
    private Boolean active;
    private Boolean featured;
    private String primaryImageUrl;
    private String categoryName;
    private String brandName;
    private Boolean inStock;
    private Boolean onSale;
    private int status;
}
