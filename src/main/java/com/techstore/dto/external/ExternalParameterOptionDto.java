package com.techstore.dto.external;

import lombok.Data;

import java.util.List;

@Data
public class ExternalParameterOptionDto {
    private Long id;
    private List<NameDto> name;
    private Integer order;
}
