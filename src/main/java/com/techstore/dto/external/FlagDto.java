package com.techstore.dto.external;

import lombok.Data;

import java.util.List;

@Data
public class FlagDto {
    private Long id;
    private String image;
    private List<NameDto> name;
}
