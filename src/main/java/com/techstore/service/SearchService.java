package com.techstore.service;

import com.techstore.dto.response.ProductSummaryDto;
import com.techstore.dto.search.GlobalProductSearchRequestDto;
import com.techstore.dto.search.GlobalSearchResponseDto;
import com.techstore.dto.search.SearchSuggestionDto;
import com.techstore.entity.Product;
import com.techstore.mapper.ProductMapper;
import com.techstore.repository.CategoryRepository;
import com.techstore.repository.ManufacturerRepository;
import com.techstore.repository.ProductRepository;
import com.techstore.repository.UserFavoriteRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.search.engine.search.predicate.SearchPredicate;
import org.hibernate.search.engine.search.predicate.dsl.BooleanPredicateClausesStep;
import org.hibernate.search.engine.search.predicate.dsl.RangePredicateFieldMoreStep;
import org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactory;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.engine.search.sort.dsl.SearchSortFactory;
import org.hibernate.search.engine.search.sort.dsl.SortFinalStep;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SearchService {

    private final EntityManager entityManager;
    private final ProductMapper productMapper;
    private final UserFavoriteRepository userFavoriteRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ManufacturerRepository manufacturerRepository;

    // Field weights for relevance scoring
    private static final Map<String, Float> FIELD_WEIGHTS = Map.of(
            "referenceNumber", 3.0f,
            "nameEn", 2.5f,
            "nameBg", 2.5f,
            "model", 2.0f,
            "manufacturer.name", 1.8f,
            "barcode", 3.0f,
            "descriptionEn", 1.0f,
            "descriptionBg", 1.0f,
            "category.nameEn", 1.5f,
            "category.nameBg", 1.5f
    );

    /**
     * Comprehensive global search across all product fields and relationships
     */
    public GlobalSearchResponseDto globalSearch(GlobalProductSearchRequestDto request, Long userId) {
        log.info("Performing global search with query: '{}', mode: {}",
                request.getQuery(), request.getSearchMode());

        long startTime = System.currentTimeMillis();

        try {
            SearchSession searchSession = Search.session(entityManager);

            // Build the search query
            var searchQuery = searchSession.search(Product.class)
                    .where(f -> buildSearchPredicate(f, request))
                    .sort(f -> (SortFinalStep) buildSortCriteria(f, request))
                    .fetch(request.getPage() * request.getSize(), request.getSize());

            List<Product> products = searchQuery.hits();
            long totalHits = searchQuery.total().hitCount();

            // Convert to DTOs
            List<ProductSummaryDto> productDtos = convertToProductSummaries(products, request.getLanguage(), userId);

            // Build response
            long searchTimeMs = System.currentTimeMillis() - startTime;

            return GlobalSearchResponseDto.builder()
                    .products(productDtos)
                    .pagination(buildPagination(request, totalHits))
                    .stats(buildSearchStats(request, totalHits, searchTimeMs, searchQuery))
                    .facets(buildFacets(searchQuery, request))
                    .suggestions(generateSuggestions(request.getQuery(), request.getLanguage()))
                    .relatedQueries(generateRelatedQueries(request.getQuery()))
                    .metadata(buildSearchMetadata(request, searchTimeMs))
                    .build();

        } catch (Exception e) {
            log.error("Error performing global search", e);
            throw new RuntimeException("Search failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get search suggestions/autocomplete
     */
    public SearchSuggestionDto getSearchSuggestions(String query, String language, int limit) {
        log.debug("Getting search suggestions for query: '{}'", query);

        long startTime = System.currentTimeMillis();

        if (!StringUtils.hasText(query) || query.trim().length() < 2) {
            return SearchSuggestionDto.builder()
                    .query(query)
                    .totalSuggestions(0)
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .productNames(List.of())
                    .manufacturers(List.of())
                    .categories(List.of())
                    .models(List.of())
                    .referenceNumbers(List.of())
                    .keywords(List.of())
                    .build();
        }

        Set<String> productNames = new HashSet<>();
        Set<String> manufacturers = new HashSet<>();
        Set<String> categories = new HashSet<>();
        Set<String> models = new HashSet<>();
        Set<String> referenceNumbers = new HashSet<>();
        Set<String> keywords = new HashSet<>();

        try {
            SearchSession searchSession = Search.session(entityManager);

            // Search for suggestions across different fields
            var suggestions = searchSession.search(Product.class)
                    .select(f -> f.composite(
                            f.field("nameEn", String.class),
                            f.field("nameBg", String.class),
                            f.field("manufacturer.name", String.class),
                            f.field("category.nameEn", String.class),
                            f.field("category.nameBg", String.class),
                            f.field("model", String.class),
                            f.field("referenceNumber", String.class)
                    ))
                    .where(f -> f.bool(b -> {
                        b.must(f.match().field("show").matching(true));
                        b.must(f.bool(textSearch -> {
                            // Prefix matches for autocomplete
                            textSearch.should(f.wildcard().field("nameEn").matching(query.toLowerCase() + "*"));
                            textSearch.should(f.wildcard().field("nameBg").matching(query.toLowerCase() + "*"));
                            textSearch.should(f.wildcard().field("manufacturer.name").matching(query.toLowerCase() + "*"));
                            textSearch.should(f.wildcard().field("model").matching(query.toLowerCase() + "*"));
                            textSearch.should(f.wildcard().field("referenceNumber").matching(query.toLowerCase() + "*"));

                            if ("bg".equals(language)) {
                                textSearch.should(f.wildcard().field("category.nameBg").matching(query.toLowerCase() + "*"));
                            } else {
                                textSearch.should(f.wildcard().field("category.nameEn").matching(query.toLowerCase() + "*"));
                            }
                        }));
                    }))
                    .fetch(limit * 2);

            for (var hit : suggestions.hits()) {
                List<?> fields = (List<?>) hit;

                String nameEn = (String) fields.get(0);
                String nameBg = (String) fields.get(1);
                String manufacturerName = (String) fields.get(2);
                String categoryEn = (String) fields.get(3);
                String categoryBg = (String) fields.get(4);
                String model = (String) fields.get(5);
                String referenceNumber = (String) fields.get(6);

                // Filter and add relevant suggestions
                if (containsIgnoreCase(nameEn, query)) productNames.add(nameEn);
                if (containsIgnoreCase(nameBg, query)) productNames.add(nameBg);
                if (containsIgnoreCase(manufacturerName, query)) manufacturers.add(manufacturerName);
                if (containsIgnoreCase(model, query)) models.add(model);
                if (containsIgnoreCase(referenceNumber, query)) referenceNumbers.add(referenceNumber);

                if ("bg".equals(language) && containsIgnoreCase(categoryBg, query)) {
                    categories.add(categoryBg);
                } else if (containsIgnoreCase(categoryEn, query)) {
                    categories.add(categoryEn);
                }

                // Extract keywords
                extractKeywords(nameEn, query, keywords);
                extractKeywords(nameBg, query, keywords);
            }

            // Limit results
            return SearchSuggestionDto.builder()
                    .query(query)
                    .productNames(limitList(productNames, limit / 6))
                    .manufacturers(limitList(manufacturers, limit / 6))
                    .categories(limitList(categories, limit / 6))
                    .models(limitList(models, limit / 6))
                    .referenceNumbers(limitList(referenceNumbers, limit / 6))
                    .keywords(limitList(keywords, limit / 6))
                    .totalSuggestions(productNames.size() + manufacturers.size() + categories.size() +
                            models.size() + referenceNumbers.size() + keywords.size())
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("Error getting search suggestions", e);
            throw new RuntimeException("Suggestions failed: " + e.getMessage(), e);
        }
    }

    /**
     * Advanced search with multiple criteria
     */
    public Page<ProductSummaryDto> advancedSearch(GlobalProductSearchRequestDto request, Long userId) {
        log.info("Performing advanced search with {} filters", countActiveFilters(request));

        SearchSession searchSession = Search.session(entityManager);

        var searchResult = searchSession.search(Product.class)
                .where(f -> buildAdvancedSearchPredicate(f, request))
                .sort(f -> (SortFinalStep) buildSortCriteria(f, request))
                .fetch(request.getPage() * request.getSize(), request.getSize());

        List<Product> products = searchResult.hits();
        long totalHits = searchResult.total().hitCount();

        List<ProductSummaryDto> productDtos = convertToProductSummaries(products, request.getLanguage(), userId);

        PageRequest pageRequest = PageRequest.of(request.getPage(), request.getSize());
        return new PageImpl<>(productDtos, pageRequest, totalHits);
    }

    // ============ PRIVATE HELPER METHODS ============

    private BooleanPredicateClausesStep<?> buildSearchPredicate(SearchPredicateFactory f,
                                                                GlobalProductSearchRequestDto request) {
        BooleanPredicateClausesStep<?> boolQuery = f.bool();

        // Basic visibility filters
        boolQuery.must(f.match().field("show").matching(true));
        if (!Boolean.TRUE.equals(request.getIncludeInactive())) {
            boolQuery.must(f.match().field("active").matching(true));
        }

        // Main search query
        if (StringUtils.hasText(request.getQuery())) {
            boolQuery.must(buildTextSearchPredicate(f, request));
        }

        // Category filters
        if (request.getCategoryId() != null) {
            boolQuery.must(f.match().field("category.id").matching(request.getCategoryId()));
        }
        if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
            boolQuery.must(f.terms().field("category.id").matchingAny(request.getCategoryIds()));
        }

        // Manufacturer filters
        if (request.getManufacturerId() != null) {
            boolQuery.must(f.match().field("manufacturer.id").matching(request.getManufacturerId()));
        }
        if (request.getManufacturerIds() != null && !request.getManufacturerIds().isEmpty()) {
            boolQuery.must(f.terms().field("manufacturer.id").matchingAny(request.getManufacturerIds()));
        }

        // Price filters
        if (request.getMinPrice() != null || request.getMaxPrice() != null) {
            var priceRange = f.range().field("finalPrice");
            if (request.getMinPrice() != null) {
                priceRange = (RangePredicateFieldMoreStep<? extends RangePredicateFieldMoreStep<?, ?>, ?>) priceRange.atLeast(request.getMinPrice());
            }
            if (request.getMaxPrice() != null) {
                priceRange = (RangePredicateFieldMoreStep<? extends RangePredicateFieldMoreStep<?, ?>, ?>) priceRange.atMost(request.getMaxPrice());
            }
            boolQuery.must((SearchPredicate) priceRange);
        }

        // Status filters
        if (request.getStatuses() != null && !request.getStatuses().isEmpty()) {
            boolQuery.must(f.terms().field("status").matchingAny(request.getStatuses()));
        }

        // Boolean filters
        if (request.getFeatured() != null) {
            boolQuery.must(f.match().field("featured").matching(request.getFeatured()));
        }
        if (request.getOnSale() != null && request.getOnSale()) {
            boolQuery.must(f.range().field("discount").greaterThan(BigDecimal.ZERO));
        }

        // Weight and warranty filters
        if (request.getMinWeight() != null || request.getMaxWeight() != null) {
            var weightRange = f.range().field("weight");
            if (request.getMinWeight() != null) {
                weightRange = (RangePredicateFieldMoreStep<? extends RangePredicateFieldMoreStep<?, ?>, ?>) weightRange.atLeast(request.getMinWeight());
            }
            if (request.getMaxWeight() != null) {
                weightRange = (RangePredicateFieldMoreStep<? extends RangePredicateFieldMoreStep<?, ?>, ?>) weightRange.atMost(request.getMaxWeight());
            }
            boolQuery.must((SearchPredicate) weightRange);
        }

        if (request.getMinWarranty() != null || request.getMaxWarranty() != null) {
            var warrantyRange = f.range().field("warranty");
            if (request.getMinWarranty() != null) {
                warrantyRange = (RangePredicateFieldMoreStep<? extends RangePredicateFieldMoreStep<?, ?>, ?>) warrantyRange.atLeast(request.getMinWarranty());
            }
            if (request.getMaxWarranty() != null) {
                warrantyRange = (RangePredicateFieldMoreStep<? extends RangePredicateFieldMoreStep<?, ?>, ?>) warrantyRange.atMost(request.getMaxWarranty());
            }
            boolQuery.must((SearchPredicate) warrantyRange);
        }

        // Parameter filters
        if (request.getParameterFilters() != null && !request.getParameterFilters().isEmpty()) {
            for (var paramFilter : request.getParameterFilters()) {
                boolQuery.must(buildParameterFilter(f, paramFilter));
            }
        }

        return boolQuery;
    }

    private BooleanPredicateClausesStep<?> buildTextSearchPredicate(SearchPredicateFactory f,
                                                                    GlobalProductSearchRequestDto request) {
        String query = request.getQuery().trim();
        String language = request.getLanguage();

        BooleanPredicateClausesStep<?> textQuery = f.bool();

        switch (request.getSearchMode() != null ? request.getSearchMode() :
                GlobalProductSearchRequestDto.SearchMode.SMART) {

            case ALL_FIELDS -> {
                // Search across all indexed fields with equal weight
                textQuery.should(f.match().field("nameEn").matching(query));
                textQuery.should(f.match().field("nameBg").matching(query));
                textQuery.should(f.match().field("descriptionEn").matching(query));
                textQuery.should(f.match().field("descriptionBg").matching(query));
                textQuery.should(f.match().field("referenceNumber").matching(query));
                textQuery.should(f.match().field("model").matching(query));
                textQuery.should(f.match().field("barcode").matching(query));
                textQuery.should(f.match().field("manufacturer.name").matching(query));
                textQuery.should(f.match().field("category.nameEn").matching(query));
                textQuery.should(f.match().field("category.nameBg").matching(query));
            }

            case SPECIFIC_FIELDS -> {
                // Search only in specified fields
                if (StringUtils.hasText(request.getProductName())) {
                    if ("bg".equals(language)) {
                        textQuery.should(f.match().field("nameBg").matching(request.getProductName()));
                    } else {
                        textQuery.should(f.match().field("nameEn").matching(request.getProductName()));
                    }
                }
                if (StringUtils.hasText(request.getReferenceNumber())) {
                    textQuery.should(f.match().field("referenceNumber").matching(request.getReferenceNumber()));
                }
                if (StringUtils.hasText(request.getModel())) {
                    textQuery.should(f.match().field("model").matching(request.getModel()));
                }
                if (StringUtils.hasText(request.getBarcode())) {
                    textQuery.should(f.match().field("barcode").matching(request.getBarcode()));
                }
            }

            case SMART -> {
                // Weighted search with boosting for important fields
                textQuery.should(f.match().field("referenceNumber").matching(query).boost(FIELD_WEIGHTS.get("referenceNumber")));
                textQuery.should(f.match().field("barcode").matching(query).boost(FIELD_WEIGHTS.get("barcode")));

                if ("bg".equals(language)) {
                    textQuery.should(f.match().field("nameBg").matching(query).boost(FIELD_WEIGHTS.get("nameBg")));
                    textQuery.should(f.match().field("descriptionBg").matching(query).boost(FIELD_WEIGHTS.get("descriptionBg")));
                    textQuery.should(f.match().field("category.nameBg").matching(query).boost(FIELD_WEIGHTS.get("category.nameBg")));
                } else {
                    textQuery.should(f.match().field("nameEn").matching(query).boost(FIELD_WEIGHTS.get("nameEn")));
                    textQuery.should(f.match().field("descriptionEn").matching(query).boost(FIELD_WEIGHTS.get("descriptionEn")));
                    textQuery.should(f.match().field("category.nameEn").matching(query).boost(FIELD_WEIGHTS.get("category.nameEn")));
                }

                textQuery.should(f.match().field("model").matching(query).boost(FIELD_WEIGHTS.get("model")));
                textQuery.should(f.match().field("manufacturer.name").matching(query).boost(FIELD_WEIGHTS.get("manufacturer.name")));

                // Add phrase matching for exact phrases
                if (query.contains(" ")) {
                    textQuery.should(f.phrase().field("nameEn").matching(query).boost(2.0f));
                    textQuery.should(f.phrase().field("nameBg").matching(query).boost(2.0f));
                }
            }
        }

        return textQuery;
    }

    private BooleanPredicateClausesStep<?> buildParameterFilter(SearchPredicateFactory f,
                                                                GlobalProductSearchRequestDto.ParameterFilter filter) {
        BooleanPredicateClausesStep<?> paramQuery = f.bool();

        if (filter.getOptionIds() != null && !filter.getOptionIds().isEmpty()) {
            if ("AND".equalsIgnoreCase(filter.getOperator())) {
                for (Long optionId : filter.getOptionIds()) {
                    paramQuery.must(f.match().field("productParameters.parameterOption.id").matching(optionId));
                }
            } else {
                paramQuery.must(f.terms().field("productParameters.parameterOption.id").matchingAny(filter.getOptionIds()));
            }
        }

        return paramQuery;
    }

    private Object buildSortCriteria(SearchSortFactory f, GlobalProductSearchRequestDto request) {
        String sortBy = request.getSortBy() != null ? request.getSortBy() : "relevance";
        boolean desc = "desc".equalsIgnoreCase(request.getSortDirection());

        return switch (sortBy.toLowerCase()) {
            case "price" -> desc ? f.field("finalPrice").desc() : f.field("finalPrice").asc();
            case "name" -> {
                String nameField = "bg".equals(request.getLanguage()) ? "nameBg" : "nameEn";
                yield desc ? f.field(nameField).desc() : f.field(nameField).asc();
            }
            case "createdat" -> desc ? f.field("createdAt").desc() : f.field("createdAt").asc();
            case "popularity" -> f.score().desc(); // Use relevance score as popularity
            default -> f.score().desc(); // Relevance-based sorting
        };
    }

    private BooleanPredicateClausesStep<?> buildAdvancedSearchPredicate(SearchPredicateFactory f,
                                                                        GlobalProductSearchRequestDto request) {
        // Similar to buildSearchPredicate but with more advanced logic
        return buildSearchPredicate(f, request);
    }

    private Object buildAggregations(GlobalProductSearchRequestDto request) {
        // Placeholder for aggregation building
        // In a full implementation, you would build category, manufacturer, price range aggregations
        return null;
    }

    private GlobalSearchResponseDto.SearchPagination buildPagination(GlobalProductSearchRequestDto request, long totalHits) {
        int totalPages = (int) Math.ceil((double) totalHits / request.getSize());

        return GlobalSearchResponseDto.SearchPagination.builder()
                .currentPage(request.getPage())
                .pageSize(request.getSize())
                .totalElements(totalHits)
                .totalPages(totalPages)
                .hasNext(request.getPage() < totalPages - 1)
                .hasPrevious(request.getPage() > 0)
                .build();
    }

    private GlobalSearchResponseDto.SearchStats buildSearchStats(GlobalProductSearchRequestDto request,
                                                                 long totalHits, long searchTimeMs,
                                                                 SearchResult<Product> searchResult) {
        return GlobalSearchResponseDto.SearchStats.builder()
                .totalFound(totalHits)
                .searchTimeMs(searchTimeMs)
                .maxScore(searchResult.total().isHitCountExact() ? 1.0 : 0.0) // Simplified
                .searchQuery(request.getQuery())
                .searchedFields(getSearchedFields(request))
                .build();
    }

    private GlobalSearchResponseDto.SearchFacets buildFacets(SearchResult<Product> searchResult,
                                                             GlobalProductSearchRequestDto request) {
        // Placeholder for facet building
        // In a full implementation, you would extract aggregation results
        return GlobalSearchResponseDto.SearchFacets.builder()
                .categories(List.of())
                .manufacturers(List.of())
                .parameters(List.of())
                .statuses(List.of())
                .build();
    }

    private GlobalSearchResponseDto.SearchMetadata buildSearchMetadata(GlobalProductSearchRequestDto request,
                                                                       long searchTimeMs) {
        return GlobalSearchResponseDto.SearchMetadata.builder()
                .originalQuery(request.getQuery())
                .processedQuery(processQuery(request.getQuery()))
                .appliedFilters(getAppliedFilters(request))
                .hasSpellingSuggestions(false) // Placeholder
                .debugInfo(Map.of("searchTimeMs", searchTimeMs))
                .build();
    }

    private List<ProductSummaryDto> convertToProductSummaries(List<Product> products, String language, Long userId) {
        return products.stream()
                .map(product -> {
                    ProductSummaryDto dto = productMapper.toSummaryDto(product, language);
                    if (userId != null) {
                        dto.setIsFavorite(userFavoriteRepository.existsByUserIdAndProductId(userId, product.getId()));
                    }
                    return dto;
                })
                .toList();
    }

    // Utility methods
    private List<String> generateSuggestions(String query, String language) {
        // Placeholder implementation
        return List.of();
    }

    private List<String> generateRelatedQueries(String query) {
        // Placeholder implementation
        return List.of();
    }

    private List<String> getSearchedFields(GlobalProductSearchRequestDto request) {
        // Return list of fields that were searched based on request
        return List.of("nameEn", "nameBg", "referenceNumber", "model", "manufacturer.name");
    }

    private String processQuery(String query) {
        return query != null ? query.trim().toLowerCase() : "";
    }

    private List<String> getAppliedFilters(GlobalProductSearchRequestDto request) {
        List<String> filters = new ArrayList<>();
        if (request.getCategoryId() != null) filters.add("category");
        if (request.getManufacturerId() != null) filters.add("manufacturer");
        if (request.getMinPrice() != null || request.getMaxPrice() != null) filters.add("price");
        return filters;
    }

    private int countActiveFilters(GlobalProductSearchRequestDto request) {
        int count = 0;
        if (request.getCategoryId() != null) count++;
        if (request.getManufacturerId() != null) count++;
        if (request.getMinPrice() != null || request.getMaxPrice() != null) count++;
        if (request.getStatuses() != null && !request.getStatuses().isEmpty()) count++;
        return count;
    }

    private boolean containsIgnoreCase(String text, String search) {
        return text != null && search != null &&
                text.toLowerCase().contains(search.toLowerCase());
    }

    private void extractKeywords(String text, String query, Set<String> keywords) {
        if (text == null || query == null) return;

        String[] words = text.toLowerCase().split("\\s+");
        String queryLower = query.toLowerCase();

        for (String word : words) {
            if (word.startsWith(queryLower) && word.length() > queryLower.length()) {
                keywords.add(word);
            }
        }
    }

    private List<String> limitList(Set<String> set, int limit) {
        return set.stream()
                .sorted()
                .limit(limit)
                .collect(Collectors.toList());
    }
}