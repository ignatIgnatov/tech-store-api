package com.techstore.dto.speedy;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class SpeedyCalculatePriceResponse {
    private List<Calculation> calculations;
    private Error error;

    @Data
    public static class Calculation {
        private Long serviceId;
        private Price price;
        private String pickupDate;
        private String deliveryDeadline;
    }

    @Data
    public static class Price {
        private BigDecimal amount;
        private BigDecimal vat;
        private BigDecimal total;
        private String currency;
        private PriceDetails details;
    }

    @Data
    public static class PriceDetails {
        private BigDecimal net;
        private BigDecimal vat;
        private BigDecimal packings;
        private BigDecimal declaredValue;
        private BigDecimal cod;
        private BigDecimal returns;
        private BigDecimal specialDelivery;
        private BigDecimal fixedTimeDelivery;
        private BigDecimal deliveryToFloor;
    }

    @Data
    public static class Error {
        private String id;
        private String message;
        private String component;
        private String context;
    }
}