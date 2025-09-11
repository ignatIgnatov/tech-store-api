package com.techstore.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkVisibilityUpdateRequestDto {
    @NotEmpty(message = "Product IDs cannot be empty")
    private List<Long> productIds;

    private Boolean show;
}
