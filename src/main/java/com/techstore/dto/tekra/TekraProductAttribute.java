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
public class TekraProductAttribute {
    private String name;
    private String value;
    private String unit;
    private String group;

    @JsonProperty("sort_order")
    private Integer sortOrder;
}