package com.techstore.util;

import com.techstore.dto.FilterOptionDTO;
import com.techstore.dto.PriceRangeDTO;
import com.techstore.dto.RangeDTO;
import com.techstore.entity.Product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class FilterUtils {

    public static PriceRangeDTO calculatePriceRange(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return PriceRangeDTO.getDefault();
        }

        BigDecimal min = products.stream()
                .map(Product::getDiscountedPrice)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal max = products.stream()
                .map(Product::getDiscountedPrice)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(new BigDecimal("999999"));

        return PriceRangeDTO.builder()
                .min(min)
                .max(max)
                .currency("BGN")
                .symbol("лв")
                .decimalPlaces(2)
                .build();
    }

    public static RangeDTO calculateNumericRange(List<String> values, String unit) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        List<BigDecimal> numericValues = values.stream()
                .map(FilterUtils::parseNumeric)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (numericValues.isEmpty()) {
            return null;
        }

        BigDecimal min = numericValues.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = numericValues.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        return RangeDTO.builder()
                .min(min)
                .max(max)
                .unit(unit)
                .build();
    }

    private static BigDecimal parseNumeric(String value) {
        try {
            // Remove any non-numeric characters except decimal point
            String cleaned = value.replaceAll("[^0-9.]", "");
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static List<FilterOptionDTO> createFilterOptions(List<String> values,
                                                            Map<String, Integer> counts) {
        return values.stream()
                .map(value -> FilterOptionDTO.builder()
                        .value(value)
                        .label(value)
                        .count(counts.getOrDefault(value, 0))
                        .selected(false)
                        .disabled(false)
                        .build())
                .sorted((o1, o2) -> Integer.compare(o2.getCount(), o1.getCount()))
                .collect(Collectors.toList());
    }
}
