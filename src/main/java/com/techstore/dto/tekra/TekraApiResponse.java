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
public class TekraApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private Integer total;
    private Integer page;

    @JsonProperty("per_page")
    private Integer perPage;

    @JsonProperty("total_pages")
    private Integer totalPages;

    public boolean isSuccess() {
        return success;
    }
}