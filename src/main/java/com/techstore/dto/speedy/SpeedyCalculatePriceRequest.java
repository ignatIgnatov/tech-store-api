package com.techstore.dto.speedy;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class SpeedyCalculatePriceRequest {

    // Authentication fields - REQUIRED for every Speedy API request
    private String userName;
    private String password;
    private String language; // Optional, default "BG"
    private Long clientSystemId; // Optional

    // Sender and Recipient
    private Sender sender;
    private Recipient recipient;

    // Calculation fields
    private Long senderSiteId;
    private Long receiverSiteId;
    private Boolean saturdayDelivery;
    private Service service;
    private Content content;
    private Payment payment;

    @Data
    public static class Sender {
        private Long clientId;
    }

    @Data
    public static class Recipient {
        private Long siteId;
        private Long officeId;
    }

    @Data
    public static class Service {
        private Long serviceId;
        private Integer deferredDays;
    }

    @Data
    public static class Content {
        private List<Parcel> parcels;
        private Boolean documents;
        private BigDecimal totalWeight;
        private Integer parcelsCount;
        private String contents;
        private String packageType;
    }

    @Data
    public static class Parcel {
        private BigDecimal weight;
        private Integer count;
        private ParcelSize size;
    }

    @Data
    public static class ParcelSize {
        private Integer width;
        private Integer height;
        private Integer depth;
    }

    @Data
    public static class Payment {
        private String courierServicePayer; // "SENDER", "RECIPIENT", or "THIRD_PARTY"
        private String declaredValuePayer;
        private String packagePayer;
        private Long thirdPartyClientId;
    }
}