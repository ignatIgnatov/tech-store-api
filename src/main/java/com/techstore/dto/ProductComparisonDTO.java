package com.techstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductComparisonDTO {
    private Long id;
    private String name;
    private String manufacturerName;
    private BigDecimal priceClient;
    private BigDecimal pricePromo;
    private BigDecimal priceClientPromo;
    private String imageUrl;
    private String brandName;
    private Map<String, String> specifications;
}
