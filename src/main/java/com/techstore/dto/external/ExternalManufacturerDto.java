package com.techstore.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ExternalManufacturerDto {
    private Long id;
    private String name;
    private InformationDto information;
    @JsonProperty("eu_representative")
    private EuRepresentativeDto euRepresentative;
}
