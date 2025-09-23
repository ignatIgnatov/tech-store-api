package com.techstore.dto.tekra;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TekraCategory {
    private String id;
    private String name;
    private String slug;
    private String description;

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("parent_id")
    private String parentId;

    @JsonProperty("parent_slug")
    private String parentSlug;

    private List<TekraCategory> children;

    @JsonProperty("sort_order")
    private Integer sortOrder;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("product_count")
    private Integer productCount;

    @JsonProperty("meta_title")
    private String metaTitle;

    @JsonProperty("meta_description")
    private String metaDescription;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;
}