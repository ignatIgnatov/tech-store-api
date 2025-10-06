package com.techstore.entity;

import com.techstore.enums.OrderStatus;
import com.techstore.enums.PaymentMethod;
import com.techstore.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Data
@EqualsAndHashCode(callSuper = false)
public class Order extends BaseEntity {

    @Column(name = "order_number", unique = true, nullable = false)
    private String orderNumber; // Уникален номер на поръчката (напр. ORD-2025-00001)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    // Цени
    @Column(name = "subtotal", precision = 10, scale = 2, nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO; // Сума без ДДС

    @Column(name = "tax_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal taxAmount = BigDecimal.ZERO; // ДДС (20%)

    @Column(name = "shipping_cost", precision = 10, scale = 2, nullable = false)
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total", precision = 10, scale = 2, nullable = false)
    private BigDecimal total = BigDecimal.ZERO; // Крайна сума с ДДС

    // Информация за клиента
    @Column(name = "customer_first_name", nullable = false)
    private String customerFirstName;

    @Column(name = "customer_last_name", nullable = false)
    private String customerLastName;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(name = "customer_phone", nullable = false)
    private String customerPhone;

    @Column(name = "customer_company")
    private String customerCompany; // За фирми

    @Column(name = "customer_vat_number")
    private String customerVatNumber; // ЕИК/БУЛСТАТ за фирми

    @Column(name = "customer_vat_registered")
    private Boolean customerVatRegistered = false; // Регистриран по ДДС

    // Адрес за доставка
    @Column(name = "shipping_address", nullable = false, columnDefinition = "TEXT")
    private String shippingAddress;

    @Column(name = "shipping_city", nullable = false)
    private String shippingCity;

    @Column(name = "shipping_postal_code")
    private String shippingPostalCode;

    @Column(name = "shipping_country", nullable = false)
    private String shippingCountry = "Bulgaria";

    // Адрес за фактура (може да е различен)
    @Column(name = "billing_address", columnDefinition = "TEXT")
    private String billingAddress;

    @Column(name = "billing_city")
    private String billingCity;

    @Column(name = "billing_postal_code")
    private String billingPostalCode;

    @Column(name = "billing_country")
    private String billingCountry = "Bulgaria";

    // Бележки
    @Column(name = "customer_notes", columnDefinition = "TEXT")
    private String customerNotes;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    // Данни за доставка
    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    // NAP данни
    @Column(name = "invoice_number")
    private String invoiceNumber; // Номер на фактура

    @Column(name = "invoice_date")
    private LocalDateTime invoiceDate;

    @Column(name = "fiscal_receipt_number")
    private String fiscalReceiptNumber; // Номер на фискален бон

    // Продукти в поръчката
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    // Helper методи
    public void calculateTotals() {
        this.subtotal = orderItems.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.taxAmount = this.subtotal.multiply(new BigDecimal("0.20")); // 20% ДДС

        this.total = this.subtotal
                .add(this.taxAmount)
                .add(this.shippingCost)
                .subtract(this.discountAmount != null ? this.discountAmount : BigDecimal.ZERO);
    }

    public void addOrderItem(OrderItem item) {
        orderItems.add(item);
        item.setOrder(this);
    }

    public void removeOrderItem(OrderItem item) {
        orderItems.remove(item);
        item.setOrder(null);
    }

    public String getFullCustomerName() {
        return customerFirstName + " " + customerLastName;
    }

    public String getFullShippingAddress() {
        return String.format("%s, %s, %s, %s",
                shippingAddress, shippingCity,
                shippingPostalCode != null ? shippingPostalCode : "",
                shippingCountry);
    }
}