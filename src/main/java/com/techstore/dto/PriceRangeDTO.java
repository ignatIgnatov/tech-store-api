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
public class PriceRangeDTO {
    private BigDecimal min;
    private BigDecimal max;
    private String currency;
    private String symbol;
    private Integer decimalPlaces;
    private String formatPattern;

    public static PriceRangeDTO getDefault() {
        return PriceRangeDTO.builder()
                .min(BigDecimal.ZERO)
                .max(new BigDecimal("999999"))
                .currency("BGN")
                .symbol("лв")
                .decimalPlaces(2)
                .formatPattern("#,##0.00")
                .build();
    }

    // Helper methods
    public boolean isValid() {
        return min != null && max != null && min.compareTo(max) <= 0;
    }

    public String getFormattedMin() {
        if (min == null) return "0";
        return formatPrice(min);
    }

    public String getFormattedMax() {
        if (max == null) return "∞";
        return formatPrice(max);
    }

    public String getFormattedRange() {
        return getFormattedMin() + " - " + getFormattedMax();
    }

    private String formatPrice(BigDecimal price) {
        if (price == null) return "0";

        String formatted = String.format("%,.2f", price);
        String symbolSuffix = symbol != null ? " " + symbol : "";
        return formatted + symbolSuffix;
    }

    public boolean contains(BigDecimal price) {
        if (!isValid() || price == null) return false;
        return price.compareTo(min) >= 0 && price.compareTo(max) <= 0;
    }

    public PriceRangeDTO expand(BigDecimal percentage) {
        if (!isValid() || percentage == null) return this;

        BigDecimal expandAmount = max.subtract(min).multiply(percentage).divide(new BigDecimal("100"));

        return PriceRangeDTO.builder()
                .min(min.subtract(expandAmount).max(BigDecimal.ZERO))
                .max(max.add(expandAmount))
                .currency(currency)
                .symbol(symbol)
                .decimalPlaces(decimalPlaces)
                .formatPattern(formatPattern)
                .build();
    }
}
