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
public class TekraManufacturer {
    private String id;
    private String name;
    private String description;

    @JsonProperty("logo_url")
    private String logoUrl;

    private String website;
    private String country;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("product_count")
    private Integer productCount;
}