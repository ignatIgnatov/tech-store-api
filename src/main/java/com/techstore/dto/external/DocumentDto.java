package com.techstore.dto.external;

import lombok.Data;

import java.util.List;

@Data
public class DocumentDto {
    private String href;
    private List<NameDto> comment;
}
