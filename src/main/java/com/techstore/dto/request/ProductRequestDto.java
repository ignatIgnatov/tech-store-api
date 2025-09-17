package com.techstore.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.techstore.dto.request.ParameterValueRequestDto;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductRequestDto {
    private Long id;
    @JsonProperty("idWF")
    private Long idWF;
    @JsonProperty("reference_number")
    private String referenceNumber;
    @JsonProperty("manufacturer_id")
    private Long manufacturerId;
    private Integer status;
    @JsonProperty("price_client")
    private BigDecimal priceClient;
    @JsonProperty("price_client_promo")
    private BigDecimal priceClientPromo;
    @JsonProperty("price_partner")
    private BigDecimal pricePartner;
    @JsonProperty("price_promo")
    private BigDecimal pricePromo;
    private Boolean show;
    private List<CategoryIdDto> categories;
    private String model;
    private String barcode;
    private Integer warranty;
    private BigDecimal weight;
    private String manufacturer;
    private List<NameDto> name;
    private List<DescriptionDto> description;
    private List<ImageDto> images;
    private List<DocumentDto> documents;
    private List<ParameterValueRequestDto> parameters;
    private List<FlagDto> flags;
    private BigDecimal markupPercentage;
}
