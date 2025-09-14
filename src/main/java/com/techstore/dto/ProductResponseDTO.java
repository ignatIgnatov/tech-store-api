package com.techstore.dto;

import com.techstore.dto.response.CategorySummaryDTO;
import com.techstore.dto.response.ManufacturerSummaryDto;
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
    private String name;
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
    private List<ProductSpecificationDTO> specifications;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean onSale;
    private int status;
}
