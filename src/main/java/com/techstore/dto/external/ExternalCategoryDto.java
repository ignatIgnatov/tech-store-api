package com.techstore.dto.external;

import lombok.Data;

import java.util.List;

@Data
public class ExternalCategoryDto {
    private Long id;
    private Long parent;
    private List<NameDto> name;
    private Boolean show;
    private Integer order;
}