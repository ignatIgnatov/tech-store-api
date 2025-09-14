package com.techstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManufacturerSummaryDto {
    private Long id;
    private String name;
    private String slug;
    private String logoUrl;
    private Boolean active;
}
