package com.techstore.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class BulkMarkupUpdateRequestDto {
    @NotEmpty(message = "Product IDs cannot be empty")
    private List<Long> productIds;

    private BigDecimal markupPercentage;

    private String operation; // SET, ADD, MULTIPLY
}