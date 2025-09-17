package com.techstore.dto.response;

import lombok.Data;

@Data
public class ProductParameterResponseDto {
    private Long parameterId;
    private String parameterName;
    private Long optionId;
    private String optionName;
}
