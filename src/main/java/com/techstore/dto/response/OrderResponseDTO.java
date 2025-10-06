package com.techstore.dto.response;

import com.techstore.enums.OrderStatus;
import com.techstore.enums.PaymentMethod;
import com.techstore.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponseDTO {

    private Long id;
    private String orderNumber;

    // Customer info
    private String customerFirstName;
    private String customerLastName;
    private String customerEmail;
    private String customerPhone;
    private String customerCompany;
    private String customerVatNumber;

    // Status
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private PaymentMethod paymentMethod;

    // Prices
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal shippingCost;
    private BigDecimal discountAmount;
    private BigDecimal total;

    // Addresses
    private String shippingAddress;
    private String shippingCity;
    private String shippingPostalCode;
    private String shippingCountry;

    private String billingAddress;
    private String billingCity;
    private String billingPostalCode;
    private String billingCountry;

    // Items
    private List<OrderItemResponseDTO> items = new ArrayList<>();

    // Notes
    private String customerNotes;
    private String adminNotes;

    // Tracking
    private String trackingNumber;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;

    // Invoice
    private String invoiceNumber;
    private LocalDateTime invoiceDate;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}