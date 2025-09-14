package com.techstore.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategorySummaryDTO {
    private Long id;
    private String nameEn;
    private String nameBg;
    private String slug;
    private Boolean show;
}
