package com.techstore.dto.response;

import lombok.Data;

@Data
public class ProductDocumentResponseDto {
    private Long id;
    private String documentUrl;
    private String comment;
}
