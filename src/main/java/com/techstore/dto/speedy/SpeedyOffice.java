package com.techstore.dto.speedy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpeedyOffice {
    private Long id;
    private String name;
    private String nameEn;
    private Long siteId;
    private Address address;

    private String workingTimeFrom;
    private String workingTimeTo;
    private String workingTimeHalfFrom;
    private String workingTimeHalfTo;
    private String workingTimeDayOffFrom;
    private String workingTimeDayOffTo;

    // Cutoff times
    private String sameDayDepartureCutoff;
    private String sameDayDepartureCutoffHalf;
    private String sameDayDepartureCutoffDayOff;

    // Parcel dimensions and weight
    private MaxParcelDimensions maxParcelDimensions;
    private Double maxParcelWeight;

    private String type; // "OFFICE" or "APT"
    private Long nearbyOfficeId;

    // Working time schedule
    private List<WorkingTimeScheduleItem> workingTimeSchedule;

    // Payment options
    private Boolean cardPaymentsAllowed;
    private Boolean cashPaymentsAllowed;

    // Pallet and cargo options
    private Boolean palletOffice;
    private Boolean cargoTypePallet;
    private Boolean cargoTypeParcel;
    private Boolean cargoTypeTyre;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Address {
        private Long countryId;
        private Long siteId;
        private String siteType;
        private String siteName;
        private String postCode;
        private Long streetId;
        private String streetType;
        private String streetName;
        private String streetNo;
        private String complexId;
        private String complexType;
        private String complexName;
        private String blockNo;
        private String entranceNo;
        private String floorNo;
        private String apartmentNo;
        private String addressNote;
        private Double x; // longitude
        private Double y; // latitude
        private String fullAddressString;
        private String siteAddressString;
        private String localAddressString;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MaxParcelDimensions {
        private Integer width;
        private Integer height;
        private Integer depth;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkingTimeScheduleItem {
        private String date; // "2025-10-09"
        private String workingTimeFrom;
        private String workingTimeTo;
        private String sameDayDepartureCutoff;
        private Boolean standardSchedule;
    }
}