package com.techstore.enums;

public enum PaymentStatus {
    PENDING,        // Чакащо плащане
    PAID,           // Платена
    FAILED,         // Неуспешно
    REFUNDED,       // Възстановена
    PARTIALLY_PAID  // Частично платена
}