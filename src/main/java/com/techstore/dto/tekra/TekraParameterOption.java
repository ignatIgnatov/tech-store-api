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
public class TekraParameterOption {
    private String id;
    private String name;
    private String value;
    private String color; // For color attributes

    @JsonProperty("image_url")
    private String imageUrl; // For visual attributes

    @JsonProperty("sort_order")
    private Integer sortOrder;
}