package com.techstore.dto.request;

import lombok.Data;

@Data
public class CategoryRequestDto {
    private Long id;
    private Long parent;
    private String nameBg;
    private String nameEn;
    private Boolean show;
    private Integer order;
    private String slug;
}
