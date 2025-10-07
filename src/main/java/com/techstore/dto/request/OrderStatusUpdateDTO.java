package com.techstore.dto.request;

import com.techstore.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderStatusUpdateDTO {

    @NotNull(message = "Order status is required")
    private OrderStatus status;

    @Size(max = 100)
    private String trackingNumber;

    @Size(max = 1000)
    private String adminNotes;
}