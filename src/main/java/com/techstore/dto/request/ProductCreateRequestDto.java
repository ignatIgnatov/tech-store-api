package com.techstore.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductCreateRequestDto {
    @NotBlank(message = "Reference number is required")
    private String referenceNumber;

    @NotBlank(message = "Name in Bulgarian is required")
    private String nameBg;

    @NotBlank(message = "Name in English is required")
    private String nameEn;

    private String descriptionBg;
    private String descriptionEn;

    @NotNull(message = "Manufacturer ID is required")
    private Long manufacturerId;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    private BigDecimal priceClient;

    private BigDecimal pricePartner;

    private BigDecimal pricePromo;

    private BigDecimal priceClientPromo;

    @DecimalMin(value = "-100.00", message = "Markup percentage must be greater than -100%")
    @DecimalMax(value = "500.00", message = "Markup percentage must be less than 500%")
    private BigDecimal markupPercentage;

    private String model;
    private String barcode;
    private Integer warrantyMonths;
    private BigDecimal weight;
    private Boolean show = true;
}