package com.techstore.dto.speedy;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SpeedyCalculatePriceResponse {
    private BigDecimal amount;
    private BigDecimal vat;
    private BigDecimal total;
    private String currency;
    private String errorDescription;
}
