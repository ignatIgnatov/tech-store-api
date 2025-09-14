package com.techstore.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.techstore.dto.external.NameDto;
import lombok.Data;

import java.util.List;

@Data
public class ParameterRequestDto {
    private Long id;
    @JsonProperty("category_id")
    private Long categoryId;
    private List<NameDto> name;
    private List<ParameterOptionRequestDto> options;
    private Integer order;
}
