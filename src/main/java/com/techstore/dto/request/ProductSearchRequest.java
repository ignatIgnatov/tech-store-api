package com.techstore.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchRequest {
    private String query;
    private String language = "bg"; // Default language
    private List<Long> categoryIds;
    private List<Long> manufacturerIds;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Boolean inStock;
    private Boolean active = true;
    private String sortBy = "relevance"; // relevance, price_asc, price_desc, name, newest
    private int page = 0;
    private int size = 20;

    // Faceted search filters
    private Map<String, List<String>> filters; // parameter filters
    private Boolean featured;
    private Boolean onSale;
}
