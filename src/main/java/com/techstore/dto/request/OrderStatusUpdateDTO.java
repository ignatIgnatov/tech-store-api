package com.techstore.dto.request;

import com.techstore.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderStatusUpdateDTO {

    @NotNull(message = "Status is required")
    private OrderStatus status;

    private String trackingNumber;
    private String adminNotes;
}