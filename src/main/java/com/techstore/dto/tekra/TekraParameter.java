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
public class TekraParameter {
    private String id;
    private String name;
    private String type; // text, number, select, multiselect, boolean
    private String unit;
    private List<TekraParameterOption> options;

    @JsonProperty("is_required")
    private Boolean isRequired;

    @JsonProperty("is_filterable")
    private Boolean isFilterable;

    @JsonProperty("sort_order")
    private Integer sortOrder;
}