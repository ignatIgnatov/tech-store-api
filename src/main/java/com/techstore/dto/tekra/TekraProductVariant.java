package com.techstore.dto.tekra;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TekraProductVariant {
    private String id;
    private String name;
    private String sku;
    private Double price;

    @JsonProperty("original_price")
    private Double originalPrice;

    @JsonProperty("in_stock")
    private Boolean inStock;

    private Integer quantity;

    @JsonProperty("image_url")
    private String imageUrl;

    private String color;
    private String size;
    private String material;
}