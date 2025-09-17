package com.techstore.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductCreateRequestDTO {

    @NotBlank(message = "Reference number is required")
    @Size(max = 100, message = "Reference number must not exceed 100 characters")
    private String referenceNumber;

    @NotBlank(message = "Product name (EN) is required")
    @Size(max = 500, message = "Product name (EN) must not exceed 500 characters")
    private String nameEn;

    @Size(max = 500, message = "Product name (BG) must not exceed 500 characters")
    private String nameBg;

    @Size(max = 2000, message = "Description (EN) must not exceed 2000 characters")
    private String descriptionEn;

    @Size(max = 2000, message = "Description (BG) must not exceed 2000 characters")
    private String descriptionBg;

    @Size(max = 100, message = "Model must not exceed 100 characters")
    private String model;

    @Size(max = 50, message = "Barcode must not exceed 50 characters")
    private String barcode;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    @NotNull(message = "Manufacturer ID is required")
    private Long manufacturerId;

    @NotNull(message = "Status is required")
    @Min(value = 0, message = "Status must be between 0 and 4")
    @Max(value = 4, message = "Status must be between 0 and 4")
    private Integer status;

    @DecimalMin(value = "0.0", message = "Price must be positive")
    @Digits(integer = 8, fraction = 2, message = "Price format is invalid")
    private BigDecimal priceClient;

    @DecimalMin(value = "0.0", message = "Price must be positive")
    @Digits(integer = 8, fraction = 2, message = "Price format is invalid")
    private BigDecimal pricePartner;

    @DecimalMin(value = "0.0", message = "Price must be positive")
    @Digits(integer = 8, fraction = 2, message = "Price format is invalid")
    private BigDecimal pricePromo;

    @DecimalMin(value = "0.0", message = "Price must be positive")
    @Digits(integer = 8, fraction = 2, message = "Price format is invalid")
    private BigDecimal priceClientPromo;

    @DecimalMin(value = "-50.0", message = "Markup percentage must be at least -50%")
    @DecimalMax(value = "200.0", message = "Markup percentage must not exceed 200%")
    @Digits(integer = 3, fraction = 2, message = "Markup percentage format is invalid")
    private BigDecimal markupPercentage = BigDecimal.valueOf(20.0);

    private Boolean show = true;

    @Min(value = 0, message = "Warranty must be positive")
    private Integer warranty;

    @DecimalMin(value = "0.0", message = "Weight must be positive")
    @Digits(integer = 5, fraction = 2, message = "Weight format is invalid")
    private BigDecimal weight;

    private Boolean active = true;
    private Boolean featured = false;

    private List<ProductParameterCreateDTO> parameters;
}