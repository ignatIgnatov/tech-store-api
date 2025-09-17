package com.techstore.dto;

import com.techstore.dto.response.CategorySummaryDTO;
import com.techstore.dto.response.ManufacturerSummaryDto;
import com.techstore.dto.response.ProductParameterResponseDto;
import com.techstore.entity.ProductParameter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponseDTO {
    private Long id;
    private String referenceNumber;
    private String model;
    private String barcode;
    private String nameEn;
    private String nameBg;
    private String descriptionEn;
    private String descriptionBg;
    private BigDecimal priceClient;
    private BigDecimal pricePartner;
    private BigDecimal pricePromo;
    private BigDecimal priceClientPromo;
    private BigDecimal discount;
    private Boolean active;
    private Boolean featured;
    private String primaryImageUrl;
    private List<String> additionalImages;
    private Integer warranty;
    private BigDecimal weight;
    private CategorySummaryDTO category;
    private ManufacturerSummaryDto manufacturer;
    private List<ProductParameterResponseDto> specifications;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean onSale;
    private int status;
    private BigDecimal markupPercentage;
    private BigDecimal finalPrice;
    private boolean show;
    private Long workflowId;
}
