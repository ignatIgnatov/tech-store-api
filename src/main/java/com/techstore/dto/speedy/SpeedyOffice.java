package com.techstore.dto.speedy;

import lombok.Data;
import java.util.List;

@Data
public class SpeedyOffice {
    private Long id;
    private String name;
    private String nameEn;
    private Long siteId;
    private Address address;
    private WorkingTimeSchedule workingTimeSchedule;
    private List<String> workingTimeHalfDay;
    private Double maxParcelDimensions;
    private Double maxParcelWeight;
    private String type;
    private Integer nearbyOfficeId;
    private List<String> palletOffice;
    private List<String> cardPaymentsAllowed;
    private List<String> cashPaymentsAllowed;

    @Data
    public static class Address {
        private Long countryId;
        private Long siteId;
        private String postCode;
        private String streetName;
        private String streetNo;
        private String complexName;
        private String addressNote;
        private Double x;
        private Double y;
        private String fullAddressString;
    }

    @Data
    public static class WorkingTimeSchedule {
        private DaySchedule monday;
        private DaySchedule tuesday;
        private DaySchedule wednesday;
        private DaySchedule thursday;
        private DaySchedule friday;
        private DaySchedule saturday;
        private DaySchedule sunday;
    }

    @Data
    public static class DaySchedule {
        private String from;
        private String to;
    }
}