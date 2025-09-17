package com.techstore.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductImageUpdateDTO {
    private String imageUrl;
    private Integer order;
    private Boolean isPrimary;
}