package com.techstore.dto.request;

import com.techstore.enums.PaymentMethod;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class OrderCreateRequestDTO {

    // Информация за клиента
    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String customerFirstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String customerLastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    private String customerEmail;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number")
    private String customerPhone;

    private String customerCompany;
    private String customerVatNumber;
    private Boolean customerVatRegistered = false;

    // Адрес за доставка
    @NotBlank(message = "Shipping address is required")
    private String shippingAddress;

    @NotBlank(message = "Shipping city is required")
    private String shippingCity;

    private String shippingPostalCode;

    @NotBlank(message = "Shipping country is required")
    private String shippingCountry = "Bulgaria";

    // Адрес за фактура (опционален, ако е различен)
    private Boolean useSameAddressForBilling = true;
    private String billingAddress;
    private String billingCity;
    private String billingPostalCode;
    private String billingCountry = "Bulgaria";

    // Продукти
    @NotEmpty(message = "Order must contain at least one item")
    private List<OrderItemCreateDTO> items = new ArrayList<>();

    // Плащане
    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    // Бележки
    @Size(max = 1000, message = "Customer notes must not exceed 1000 characters")
    private String customerNotes;

    // Транспорт
    @DecimalMin(value = "0.0", message = "Shipping cost must be positive")
    private BigDecimal shippingCost = BigDecimal.ZERO;

    // Промо код (опционално)
    private String promoCode;
}