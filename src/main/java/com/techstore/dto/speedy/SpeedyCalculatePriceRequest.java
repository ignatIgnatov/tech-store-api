package com.techstore.dto.speedy;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SpeedyCalculatePriceRequest {
    private Long senderSiteId;
    private Long receiverSiteId;
    private Service service;
    private Content content;
    private Payer payer;
    private Boolean saturdayDelivery;

    @Data
    public static class Service {
        private Long serviceId;
    }

    @Data
    public static class Content {
        private List<Parcel> parcels;
        private Boolean documents;
        private BigDecimal totalWeight;
    }

    @Data
    public static class Parcel {
        private BigDecimal weight;
        private Integer count;
    }

    @Data
    public static class Payer {
        private String type;
    }
}
