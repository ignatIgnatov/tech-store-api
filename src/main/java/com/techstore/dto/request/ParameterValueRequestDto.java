package com.techstore.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.techstore.dto.external.NameDto;
import lombok.Data;

import java.util.List;

@Data
public class ParameterValueRequestDto {
    @JsonProperty("parameter_id")
    private Long parameterId;
    @JsonProperty("parameter_name")
    private List<NameDto> parameterName;
    @JsonProperty("option_id")
    private Long optionId;
    @JsonProperty("option_name")
    private List<NameDto> optionName;
}
