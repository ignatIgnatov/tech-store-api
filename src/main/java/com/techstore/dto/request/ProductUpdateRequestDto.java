package com.techstore.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductUpdateRequestDto {
    private String nameBg;
    private String nameEn;
    private String descriptionBg;
    private String descriptionEn;

    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal priceClient;

    @DecimalMin(value = "-100.00", message = "Markup percentage must be greater than -100%")
    @DecimalMax(value = "500.00", message = "Markup percentage must be less than 500%")
    private BigDecimal markupPercentage;

    private String model;
    private String barcode;
    private Integer warrantyMonths;
    private BigDecimal weight;
    private Boolean show;
}
