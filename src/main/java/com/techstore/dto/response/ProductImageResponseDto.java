package com.techstore.dto.response;

import lombok.Data;

@Data
public class ProductImageResponseDto {
    private Long id;
    private String imageUrl;
    private Integer order;
    private Boolean isPrimary;
}
