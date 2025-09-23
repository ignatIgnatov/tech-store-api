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
public class TekraSyncRequest {
    private String action; // categories, products, single_product

    @JsonProperty("category_slug")
    private String categorySlug;

    @JsonProperty("product_ids")
    private List<String> productIds;

    @JsonProperty("last_sync_date")
    private String lastSyncDate; // Use String instead of LocalDateTime

    @JsonProperty("full_sync")
    private Boolean fullSync;

    @JsonProperty("batch_size")
    private Integer batchSize;
}