package com.techstore.dto.request;

import com.techstore.dto.external.NameDto;
import lombok.Data;

import java.util.List;

@Data
public class ParameterOptionRequestDto {
    private Long id;
    private List<NameDto> name;
    private Integer order;
}
