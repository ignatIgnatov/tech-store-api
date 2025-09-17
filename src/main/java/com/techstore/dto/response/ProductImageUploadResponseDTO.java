package com.techstore.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageUploadResponseDTO {
    private String imageUrl;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private Integer order;
    private Boolean isPrimary;
}