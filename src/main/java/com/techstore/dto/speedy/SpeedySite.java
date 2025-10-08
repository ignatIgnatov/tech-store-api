package com.techstore.dto.speedy;

import lombok.Data;

@Data
public class SpeedySite {
    private Long id;
    private Long countryId;
    private String type;
    private String typeEn;
    private String name;
    private String nameEn;
    private String municipality;
    private String municipalityEn;
    private String region;
    private String regionEn;
    private String postCode;
    private Integer addressNomenclature;
    private Double x;
    private Double y;
    private String servingDays;
    private Long servingOfficeId;
    private Long servingHubOfficeId;
}