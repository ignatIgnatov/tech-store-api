package com.techstore.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequestDTO {

    @NotBlank(message = "Product name is required")
    @Size(max = 500, message = "Product name must not exceed 500 characters")
    private String name;

    @NotBlank(message = "SKU is required")
    @Size(max = 100, message = "SKU must not exceed 100 characters")
    private String sku;

    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    @Digits(integer = 8, fraction = 2, message = "Price format is invalid")
    private BigDecimal price;

    @Digits(integer = 6, fraction = 2, message = "Discount format is invalid")
    private BigDecimal discount;

    @Min(value = 0, message = "Stock quantity cannot be negative")
    private Integer stockQuantity;

    private Boolean active = true;
    private Boolean featured = false;

    @Size(max = 1000, message = "Image URL must not exceed 1000 characters")
    private String imageUrl;

    private List<String> additionalImages;

    @Size(max = 100, message = "Warranty must not exceed 100 characters")
    private String warranty;

    @DecimalMin(value = "0.0", message = "Weight cannot be negative")
    @Digits(integer = 3, fraction = 2, message = "Weight format is invalid")
    private BigDecimal weight;

    @Size(max = 200, message = "Dimensions must not exceed 200 characters")
    private String dimensions;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    @NotNull(message = "Brand ID is required")
    private Long brandId;

    private List<ProductSpecificationRequestDTO> specifications;
}
