package com.techstore.enums;

public enum PaymentStatus {
    PENDING,        // Чакащо плащане
    PAID,           // Платено
    FAILED,         // Неуспешно
    REFUNDED,       // Възстановено
    PARTIALLY_PAID  // Частично платено
}