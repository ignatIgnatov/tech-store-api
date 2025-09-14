package com.techstore.dto.response;

import com.techstore.enums.ProductStatus;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductSummaryDto {
    private Long id;
    private Long externalId;
    private String referenceNumber;
    private String name;
    private String manufacturer;
    private BigDecimal finalPrice;
    private ProductStatus status;
    private String statusName;
    private String mainImageUrl;
    private Boolean isFavorite;
}
