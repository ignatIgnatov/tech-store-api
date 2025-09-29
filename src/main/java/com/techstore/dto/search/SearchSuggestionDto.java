package com.techstore.dto.search;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchSuggestionDto {
    private List<String> productNames;
    private List<String> manufacturers;
    private List<String> categories;
    private List<String> models;
    private List<String> referenceNumbers;
    private List<String> keywords;

    private int totalSuggestions;
    private String query;
    private long responseTimeMs;
}
