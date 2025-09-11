package com.techstore.dto.response;

import com.techstore.enums.ProductStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductResponseDto {
    private Long id;
    private Long externalId;
    private String referenceNumber;
    private String model;
    private String barcode;
    private String name;
    private String description;
    private ManufacturerResponseDto manufacturer;
    private ProductStatus status;
    private String statusName;
    private BigDecimal priceClient;
    private BigDecimal markupPercentage;
    private BigDecimal finalPrice;
    private Integer warrantyMonths;
    private BigDecimal weight;
    private List<CategoryResponseDto> categories;
    private List<ProductParameterResponseDto> parameters;
    private List<ProductImageResponseDto> images;
    private List<ProductDocumentResponseDto> documents;
    private List<ProductFlagResponseDto> flags;
    private Boolean isFavorite;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
