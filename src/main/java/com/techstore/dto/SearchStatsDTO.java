package com.techstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchStatsDTO {
    private String searchQuery;
    private Integer totalProducts;
    private Map<String, Integer> specificationMatches;
    private Long searchTime;
    private List<String> suggestions;
}
