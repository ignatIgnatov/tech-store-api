package com.techstore.dto.tekra;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TekraProduct {
    private String id;
    private String name;
    private String description;

    @JsonProperty("short_description")
    private String shortDescription;

    private Double price;

    @JsonProperty("original_price")
    private Double originalPrice;

    @JsonProperty("discount_price")
    private Double discountPrice;

    private String currency;
    private String sku;
    private String model;
    private String brand;
    private String category;

    @JsonProperty("category_slug")
    private String categorySlug;

    @JsonProperty("in_stock")
    private Boolean inStock;

    private Integer quantity;

    @JsonProperty("image_url")
    private String imageUrl;

    private List<String> images;
    private String url;
    private String warranty;
    private Double weight;
    private String dimensions;
    private Map<String, String> specifications;
    private List<String> tags;

    @JsonProperty("is_promo")
    private Boolean isPromo;

    @JsonProperty("is_new")
    private Boolean isNew;

    @JsonProperty("is_featured")
    private Boolean isFeatured;

    @JsonProperty("view_count")
    private Integer viewCount;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("meta_title")
    private String metaTitle;

    @JsonProperty("meta_description")
    private String metaDescription;

    private List<TekraProductVariant> variants;
    private List<TekraProductAttribute> attributes;
}