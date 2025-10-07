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
public class OrderItemResponseDTO {

    private Long id;
    private Long productId;

    // Product snapshot
    private String productName;
    private String productSku;
    private String productModel;
    private String productImageUrl;

    // Pricing
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal taxRate;
    private BigDecimal lineTotal;
    private BigDecimal lineTax;
    private BigDecimal discountAmount;

    // Computed
    public BigDecimal getLineTotalWithTax() {
        return lineTotal.add(lineTax);
    }

    public BigDecimal getTotalPrice() {
        return getLineTotalWithTax();
    }
}