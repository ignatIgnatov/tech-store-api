package com.techstore.dto.tekra;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class TekraProduct {

    @JacksonXmlProperty(localName = "id")
    private Long id;

    @JacksonXmlProperty(localName = "name")
    private String name;

    @JacksonXmlProperty(localName = "description")
    private String description;

    @JacksonXmlProperty(localName = "shortDescription")
    private String shortDescription;

    @JacksonXmlProperty(localName = "sku")
    private String sku;

    @JacksonXmlProperty(localName = "model")
    private String model;

    @JacksonXmlProperty(localName = "barcode")
    private String barcode;

    @JacksonXmlProperty(localName = "price")
    private BigDecimal price;

    @JacksonXmlProperty(localName = "pricePromo")
    private BigDecimal pricePromo;

    @JacksonXmlProperty(localName = "currency")
    private String currency;

    @JacksonXmlProperty(localName = "availability")
    private String availability;

    @JacksonXmlProperty(localName = "inStock")
    private Boolean inStock;

    @JacksonXmlProperty(localName = "quantity")
    private Integer quantity;

    @JacksonXmlProperty(localName = "weight")
    private BigDecimal weight;

    @JacksonXmlProperty(localName = "warranty")
    private Integer warranty;

    @JacksonXmlProperty(localName = "category")
    private TekraCategory category;

    @JacksonXmlProperty(localName = "manufacturer")
    private TekraManufacturer manufacturer;

    @JacksonXmlElementWrapper(localName = "images")
    @JacksonXmlProperty(localName = "image")
    private List<TekraImage> images;

    @JacksonXmlElementWrapper(localName = "parameters")
    @JacksonXmlProperty(localName = "parameter")
    private List<TekraParameter> parameters;

    @JacksonXmlElementWrapper(localName = "tags")
    @JacksonXmlProperty(localName = "tag")
    private List<String> tags;

    @JacksonXmlProperty(localName = "url")
    private String url;

    @JacksonXmlProperty(localName = "dateCreated")
    private String dateCreated;

    @JacksonXmlProperty(localName = "dateModified")
    private String dateModified;

    @JacksonXmlProperty(localName = "isActive")
    private Boolean isActive;

    @JacksonXmlProperty(localName = "isFeatured")
    private Boolean isFeatured;

    @JacksonXmlProperty(localName = "discount")
    private BigDecimal discount;

    @JacksonXmlProperty(localName = "discountPercent")
    private BigDecimal discountPercent;
}