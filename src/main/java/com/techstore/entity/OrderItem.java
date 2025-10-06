package com.techstore.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Data
@EqualsAndHashCode(callSuper = false, exclude = {"order"})
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // Копие на продуктови данни (за исторически записи)
    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_sku")
    private String productSku;

    @Column(name = "product_model")
    private String productModel;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice; // Цена без ДДС за 1 бр

    @Column(name = "tax_rate", precision = 5, scale = 2, nullable = false)
    private BigDecimal taxRate = new BigDecimal("20.00"); // 20% ДДС

    @Column(name = "line_total", precision = 10, scale = 2, nullable = false)
    private BigDecimal lineTotal; // quantity * unitPrice

    @Column(name = "line_tax", precision = 10, scale = 2, nullable = false)
    private BigDecimal lineTax; // lineTotal * taxRate / 100

    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    // Helper методи
    public void calculateLineTotals() {
        this.lineTotal = this.unitPrice
                .multiply(new BigDecimal(quantity))
                .subtract(this.discountAmount != null ? this.discountAmount : BigDecimal.ZERO);

        this.lineTax = this.lineTotal
                .multiply(this.taxRate)
                .divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);
    }

    public BigDecimal getLineTotalWithTax() {
        return this.lineTotal.add(this.lineTax);
    }
}