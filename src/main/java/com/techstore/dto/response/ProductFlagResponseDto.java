package com.techstore.dto.response;

import lombok.Data;

@Data
public class ProductFlagResponseDto {
    private Long id;
    private Long externalId;
    private String imageUrl;
    private String name;
}
