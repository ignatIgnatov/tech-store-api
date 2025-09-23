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
public class TekraSyncResponse {
    private Boolean success;
    private String message;

    @JsonProperty("total_processed")
    private Long totalProcessed;

    private Long created;
    private Long updated;
    private Long errors;
    private Long skipped;

    @JsonProperty("duration_ms")
    private Long durationMs;
}