package com.techstore.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductSummaryDto {
    private Long id;
    private String nameEn;
    private String nameBg;
    private BigDecimal finalPrice;
    private String primaryImageUrl;
}
