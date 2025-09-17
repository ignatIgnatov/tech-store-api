package com.techstore.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProductParameterCreateDTO {

    @NotNull(message = "Parameter ID is required")
    private Long parameterId;

    @NotNull(message = "Parameter option ID is required")
    private Long parameterOptionId;
}