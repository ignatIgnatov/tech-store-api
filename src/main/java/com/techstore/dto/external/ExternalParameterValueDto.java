package com.techstore.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ExternalParameterValueDto {
    @JsonProperty("parameter_id")
    private Long parameterId;
    @JsonProperty("parameter_name")
    private List<NameDto> parameterName;
    @JsonProperty("option_id")
    private Long optionId;
    @JsonProperty("option_name")
    private List<NameDto> optionName;
}
