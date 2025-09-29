package com.techstore.dto.search;

import com.techstore.dto.response.ProductSummaryDto;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalSearchResponseDto {

    private List<ProductSummaryDto> products;

    private SearchPagination pagination;

    private SearchStats stats;

    private SearchFacets facets;

    private List<String> suggestions;

    private List<String> relatedQueries;

    private SearchMetadata metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchPagination {
        private int currentPage;
        private int pageSize;
        private long totalElements;
        private int totalPages;
        private boolean hasNext;
        private boolean hasPrevious;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchStats {
        private long totalFound;
        private long searchTimeMs;
        private double maxScore;
        private String searchQuery;
        private List<String> searchedFields;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchFacets {
        private List<CategoryFacet> categories;
        private List<ManufacturerFacet> manufacturers;
        private List<ParameterFacet> parameters;
        private PriceFacet priceRanges;
        private List<StatusFacet> statuses;

        @Data
        @Builder
        public static class CategoryFacet {
            private Long id;
            private String name;
            private long count;
        }

        @Data
        @Builder
        public static class ManufacturerFacet {
            private Long id;
            private String name;
            private long count;
        }

        @Data
        @Builder
        public static class ParameterFacet {
            private Long parameterId;
            private String parameterName;
            private List<OptionFacet> options;

            @Data
            @Builder
            public static class OptionFacet {
                private Long optionId;
                private String optionName;
                private long count;
            }
        }

        @Data
        @Builder
        public static class PriceFacet {
            private List<PriceRange> ranges;

            @Data
            @Builder
            public static class PriceRange {
                private String label;
                private double minPrice;
                private double maxPrice;
                private long count;
            }
        }

        @Data
        @Builder
        public static class StatusFacet {
            private String status;
            private long count;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchMetadata {
        private String originalQuery;
        private String processedQuery;
        private List<String> appliedFilters;
        private boolean hasSpellingSuggestions;
        private String correctedQuery;
        private Map<String, Object> debugInfo;
    }
}
