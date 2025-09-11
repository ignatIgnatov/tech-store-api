package com.techstore.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ExternalProductDto {
    private Long id;
    @JsonProperty("idWF")
    private Long idWF;
    @JsonProperty("reference_number")
    private String referenceNumber;
    private String model;
    private String barcode;
    private List<CategoryIdDto> categories;
    @JsonProperty("manufacturer_id")
    private Long manufacturerId;
    private String manufacturer;
    private Integer status;
    @JsonProperty("price_client")
    private BigDecimal priceClient;
    @JsonProperty("price_partner")
    private BigDecimal pricePartner;
    @JsonProperty("price_promo")
    private BigDecimal pricePromo;
    @JsonProperty("price_client_promo")
    private BigDecimal priceClientPromo;
    private Boolean show;
    private Integer warranty;
    private BigDecimal weight;
    private List<NameDto> name;
    private List<DescriptionDto> description;
    private List<ExternalParameterValueDto> parameters;
    private List<ImageDto> images;
    private List<DocumentDto> documents;
    private List<FlagDto> flags;
}
