package com.techstore.repository;

import com.techstore.dto.request.ProductSearchRequest;
import com.techstore.dto.response.ProductSearchResponse;
import com.techstore.dto.response.ProductSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ProductSearchRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public ProductSearchResponse searchProducts(ProductSearchRequest request) {
        String language = "simple";
        String nameField = request.getLanguage().equals("en") ? "name_en" : "name_bg";
        String descriptionField = request.getLanguage().equals("en") ? "description_en" : "description_bg";

        StringBuilder sql = new StringBuilder();
        StringBuilder countSql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        sql.append("SELECT p.*, m.name as manufacturer_name, c.")
                .append(nameField)
                .append(" as category_name ");

        if (StringUtils.hasText(request.getQuery())) {
            sql.append(", ts_rank(")
                    .append("to_tsvector('").append(language).append("', ")
                    .append("coalesce(p.").append(nameField).append(", '') || ' ' || ")
                    .append("coalesce(p.").append(descriptionField).append(", '') || ' ' || ")
                    .append("coalesce(p.model, '') || ' ' || ")
                    .append("coalesce(p.reference_number, '')), ")
                    .append("plainto_tsquery('").append(language).append("', :query)) as search_rank ");
            params.put("query", request.getQuery());
        } else {
            sql.append(", 1.0 as search_rank ");
        }

        sql.append("FROM products p ")
                .append("LEFT JOIN manufacturers m ON p.manufacturer_id = m.id ")
                .append("LEFT JOIN categories c ON p.category_id = c.id ");

        countSql.append("SELECT COUNT(*) FROM products p ")
                .append("LEFT JOIN manufacturers m ON p.manufacturer_id = m.id ")
                .append("LEFT JOIN categories c ON p.category_id = c.id ");

        StringBuilder whereClause = new StringBuilder("WHERE p.active = true AND p.show_flag = true ");

        if (StringUtils.hasText(request.getQuery())) {
            whereClause.append("AND (")
                    .append("to_tsvector('").append(language).append("', ")
                    .append("coalesce(p.").append(nameField).append(", '') || ' ' || ")
                    .append("coalesce(p.").append(descriptionField).append(", '') || ' ' || ")
                    .append("coalesce(p.model, '') || ' ' || ")
                    .append("coalesce(p.reference_number, '')) ")
                    .append("@@ plainto_tsquery('").append(language).append("', :query) OR ")

                    .append("LOWER(p.model) LIKE LOWER(:likeQuery) OR ")
                    .append("LOWER(p.reference_number) LIKE LOWER(:likeQuery) OR ")
                    .append("p.barcode = :exactQuery OR ")
                    .append("LOWER(m.name) LIKE LOWER(:likeQuery)) ");

            params.put("likeQuery", "%" + request.getQuery() + "%");
            params.put("exactQuery", request.getQuery());
        }

        if (request.getMinPrice() != null) {
            whereClause.append("AND p.final_price >= :minPrice ");
            params.put("minPrice", request.getMinPrice());
        }
        if (request.getMaxPrice() != null) {
            whereClause.append("AND p.final_price <= :maxPrice ");
            params.put("maxPrice", request.getMaxPrice());
        }

        if (request.getCategories() != null && !request.getCategories().isEmpty()) {
            whereClause.append("AND (LOWER(c.name_en) IN (:categories) OR LOWER(c.name_bg) IN (:categories)) ");
            List<String> lowerCategoryNames = request.getCategories().stream()
                    .map(String::toLowerCase)
                    .toList();
            params.put("categories", lowerCategoryNames);
        }

        if (request.getManufacturers() != null && !request.getManufacturers().isEmpty()) {
            whereClause.append("AND LOWER(m.name) IN (:manufacturers) ");
            List<String> lowerManufacturerNames = request.getManufacturers().stream()
                    .map(String::toLowerCase)
                    .toList();
            params.put("manufacturers", lowerManufacturerNames);
        }

        if (request.getFeatured() != null) {
            whereClause.append("AND p.featured = :featured ");
            params.put("featured", request.getFeatured());
        }

        if (request.getOnSale() != null && request.getOnSale()) {
            whereClause.append("AND p.discount > 0 ");
        }

        sql.append(whereClause);
        countSql.append(whereClause);

        sql.append("ORDER BY ");
        switch (request.getSortBy().toLowerCase()) {
            case "price_asc":
                sql.append("p.final_price ASC");
                break;
            case "price_desc":
                sql.append("p.final_price DESC");
                break;
            case "name":
                sql.append("p.").append(nameField).append(" ASC");
                break;
            case "newest":
                sql.append("p.created_at DESC");
                break;
            case "featured":
                sql.append("p.featured DESC, search_rank DESC");
                break;
            default:
                sql.append("search_rank DESC, p.final_price ASC");
        }

        sql.append(" LIMIT :limit OFFSET :offset");
        params.put("limit", request.getSize());
        params.put("offset", request.getPage() * request.getSize());

        try {
            List<ProductSearchResult> products = namedJdbcTemplate.query(
                    sql.toString(), params, (rs, rowNum) -> mapRowToProduct(rs, rowNum, request.getLanguage()));

            Long totalCount = namedJdbcTemplate.queryForObject(
                    countSql.toString(), params, Long.class);

            long totalElements = totalCount != null ? totalCount : 0;
            int totalPages = (int) Math.ceil((double) totalElements / request.getSize());

            log.info("Search executed successfully. Query: '{}', Results: {}, Time: {}ms",
                    request.getQuery(), totalElements, 0);

            return ProductSearchResponse.builder()
                    .products(products)
                    .totalElements(totalElements)
                    .totalPages(totalPages)
                    .currentPage(request.getPage())
                    .facets(new HashMap<>())
                    .searchTime(0L)
                    .build();

        } catch (Exception e) {
            log.error("PostgreSQL search failed for query: '{}'. Error: {}", request.getQuery(), e.getMessage(), e);
            throw new RuntimeException("Search failed: " + e.getMessage(), e);
        }
    }

    private ProductSearchResult mapRowToProduct(ResultSet rs, int rowNum, String language) throws SQLException {
        return ProductSearchResult.builder()
                .id(rs.getLong("id"))
                .name(rs.getString(language.equals("en") ? "name_en" : "name_bg"))
                .description(rs.getString(language.equals("en") ? "description_en" : "description_bg"))
                .model(rs.getString("model"))
                .referenceNumber(rs.getString("reference_number"))
                .finalPrice(rs.getBigDecimal("final_price"))
                .discount(rs.getBigDecimal("discount"))
                .primaryImageUrl(rs.getString("image_url"))
                .manufacturerName(rs.getString("manufacturer_name"))
                .categoryName(rs.getString("category_name"))
                .featured(rs.getBoolean("featured"))
                .onSale(rs.getBigDecimal("discount") != null && rs.getBigDecimal("discount").compareTo(BigDecimal.ZERO) > 0)
                .score(rs.getFloat("search_rank"))
                .build();
    }

    public List<String> getSearchSuggestions(String query, String language, int maxSuggestions) {
        if (!StringUtils.hasText(query) || query.length() < 2) {
            return Collections.emptyList();
        }

        try {
            String nameField = language.equals("en") ? "name_en" : "name_bg";

            String sql = "SELECT DISTINCT " + nameField + ", " +
                    "similarity(" + nameField + ", :query) as sim " +
                    "FROM products " +
                    "WHERE " + nameField + " % :query " +
                    "AND active = true AND show_flag = true " +
                    "AND " + nameField + " IS NOT NULL " +
                    "ORDER BY sim DESC, " + nameField + " " +
                    "LIMIT :limit";

            Map<String, Object> params = new HashMap<>();
            params.put("query", query);
            params.put("limit", maxSuggestions);

            return namedJdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getString(nameField))
                    .stream()
                    .filter(Objects::nonNull)
                    .toList();

        } catch (Exception e) {
            log.warn("Trigram suggestions failed, falling back to LIKE: {}", e.getMessage());

            try {
                String nameField = language.equals("en") ? "name_en" : "name_bg";
                String sql = "SELECT DISTINCT " + nameField + " " +
                        "FROM products " +
                        "WHERE LOWER(" + nameField + ") LIKE LOWER(:query) " +
                        "AND active = true AND show_flag = true " +
                        "AND " + nameField + " IS NOT NULL " +
                        "ORDER BY " + nameField + " " +
                        "LIMIT :limit";

                Map<String, Object> params = new HashMap<>();
                params.put("query", query + "%");
                params.put("limit", maxSuggestions);

                return namedJdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getString(nameField))
                        .stream()
                        .filter(Objects::nonNull)
                        .toList();

            } catch (Exception fallbackError) {
                log.error("Both trigram and LIKE suggestions failed: {}", fallbackError.getMessage());
                return Collections.emptyList();
            }
        }
    }

    public List<String> getAvailableManufacturerNames() {
        String sql = "SELECT DISTINCT name FROM manufacturers WHERE name IS NOT NULL ORDER BY name";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    public Map<String, List<String>> getAvailableCategoryNames() {
        List<String> englishNames = jdbcTemplate.queryForList(
                "SELECT DISTINCT name_en FROM categories WHERE name_en IS NOT NULL ORDER BY name_en",
                String.class
        );
        List<String> bulgarianNames = jdbcTemplate.queryForList(
                "SELECT DISTINCT name_bg FROM categories WHERE name_bg IS NOT NULL ORDER BY name_bg",
                String.class
        );

        Map<String, List<String>> result = new HashMap<>();
        result.put("en", englishNames);
        result.put("bg", bulgarianNames);
        return result;
    }
}