package com.techstore.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ManufacturerRequestDto {
    private Long id;
    private String name;
    private ManufacturerInformationDto information;
    @JsonProperty("eu_representative")
    private ManufacturerEuRepresentativeDto euRepresentative;
}
