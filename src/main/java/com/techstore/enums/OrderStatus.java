package com.techstore.enums;

public enum OrderStatus {
    PENDING,        // Чакаща обработка
    CONFIRMED,      // Потвърдена
    PROCESSING,     // В обработка
    SHIPPED,        // Изпратена
    DELIVERED,      // Доставена
    CANCELLED,      // Отказана
    REFUNDED        // Възстановена
}