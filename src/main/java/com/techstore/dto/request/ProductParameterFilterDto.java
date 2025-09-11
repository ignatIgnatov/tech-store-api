package com.techstore.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class ProductParameterFilterDto {
    private Long parameterId;
    private List<Long> optionIds;
}
