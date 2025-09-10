package com.techstore.dto;

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
    private String sku;
    private String description;
    private BigDecimal price;
    private BigDecimal discount;
    private BigDecimal discountedPrice;
    private Integer stockQuantity;
    private Boolean active;
    private Boolean featured;
    private String imageUrl;
    private List<String> additionalImages;
    private String warranty;
    private BigDecimal weight;
    private String dimensions;
    private CategorySummaryDTO category;
    private BrandSummaryDTO brand;
    private List<ProductSpecificationDTO> specifications;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean inStock;
    private Boolean onSale;
}
