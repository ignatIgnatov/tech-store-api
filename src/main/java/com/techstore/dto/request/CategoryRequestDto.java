package com.techstore.dto.request;

import com.techstore.dto.external.NameDto;
import lombok.Data;

import java.util.List;

@Data
public class CategoryRequestDto {
    private Long id;
    private Long parent;
    private List<NameDto> name;
    private Boolean show;
    private Integer order;
    private String slug;
}