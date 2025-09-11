package com.techstore.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ExternalParameterDto {
    private Long id;
    @JsonProperty("category_id")
    private Long categoryId;
    private List<NameDto> name;
    private List<ExternalParameterOptionDto> options;
    private Integer order;
}
