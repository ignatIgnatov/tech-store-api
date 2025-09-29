package com.techstore.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class SearchIndexManager {

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.search.postgresql.auto-create-indexes:true}")
    private boolean autoCreateIndexes;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeSearchIndexes() {
        if (!autoCreateIndexes) {
            log.info("Auto-creation of search indexes is disabled");
            return;
        }

        try {
            log.info("Starting search indexes initialization...");
            long startTime = System.currentTimeMillis();

            validatePostgreSQLCapabilities();

            createFullTextSearchIndexes();
            createTrigramIndexes();
            createFilteringIndexes();
            createCompositeIndexes();

            updateTableStatistics();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Search indexes initialization completed successfully in {}ms", duration);

            performanceTest();

        } catch (Exception e) {
            log.error("Failed to initialize search indexes: {}", e.getMessage(), e);
        }
    }

    private void validatePostgreSQLCapabilities() {
        try {
            jdbcTemplate.queryForObject(
                    "SELECT to_tsvector('simple', 'test')", String.class);
            log.debug("Full-text search capabilities confirmed");

            jdbcTemplate.queryForObject(
                    "SELECT similarity('test', 'test')", Float.class);
            log.debug("Trigram search capabilities confirmed");

        } catch (Exception e) {
            throw new RuntimeException("PostgreSQL full-text search capabilities not available: " + e.getMessage(), e);
        }
    }

    private void createFullTextSearchIndexes() {
        log.info("Creating full-text search indexes...");

        String[] indexes = {
                """
            CREATE INDEX IF NOT EXISTS idx_products_search_fulltext 
            ON products USING gin(to_tsvector('simple', 
                coalesce(name_bg, '') || ' ' || 
                coalesce(name_en, '') || ' ' ||
                coalesce(description_bg, '') || ' ' || 
                coalesce(description_en, '') || ' ' ||
                coalesce(model, '') || ' ' || 
                coalesce(reference_number, '')))
            """
        };

        executeIndexes(indexes, "full-text search");
    }

    private void createTrigramIndexes() {
        log.info("Creating trigram indexes for autocomplete...");

        String[] indexes = {
                """
            CREATE INDEX IF NOT EXISTS idx_products_name_bg_trgm 
            ON products USING gin(name_bg gin_trgm_ops) 
            WHERE active = true AND name_bg IS NOT NULL
            """,

                """
            CREATE INDEX IF NOT EXISTS idx_products_name_en_trgm 
            ON products USING gin(name_en gin_trgm_ops) 
            WHERE active = true AND name_en IS NOT NULL
            """,

                """
            CREATE INDEX IF NOT EXISTS idx_manufacturers_name_trgm 
            ON manufacturers USING gin(name gin_trgm_ops)
            """
        };

        executeIndexes(indexes, "trigram");
    }

    private void createFilteringIndexes() {
        log.info("Creating filtering and sorting indexes...");

        String[] indexes = {
                """
            CREATE INDEX IF NOT EXISTS idx_products_final_price_filtered 
            ON products(final_price) WHERE active = true
            """,

                """
            CREATE INDEX IF NOT EXISTS idx_products_created_at_filtered 
            ON products(created_at DESC) WHERE active = true
            """,

                """
            CREATE INDEX IF NOT EXISTS idx_products_active
            ON products(active)
            """,

                """
            CREATE INDEX IF NOT EXISTS idx_products_featured_filtered
            ON products(featured DESC) WHERE active = true
            """,

                """
            CREATE INDEX IF NOT EXISTS idx_products_category_filtered
            ON products(category_id) WHERE active = true
            """,

                """
            CREATE INDEX IF NOT EXISTS idx_products_manufacturer_filtered
            ON products(manufacturer_id) WHERE active = true
            """,

                """
            CREATE INDEX IF NOT EXISTS idx_products_barcode
            ON products(barcode) WHERE barcode IS NOT NULL AND active = true
            """
        };

        executeIndexes(indexes, "filtering");
    }

    private void createCompositeIndexes() {
        log.info("Creating composite indexes for complex queries...");

        String[] indexes = {
                """
            CREATE INDEX IF NOT EXISTS idx_products_search_composite 
            ON products(active, featured DESC, final_price ASC, created_at DESC) 
            WHERE active = true
            """
        };

        executeIndexes(indexes, "composite");
    }

    private void executeIndexes(String[] indexes, String type) {
        int created = 0;
        int skipped = 0;

        for (String indexSQL : indexes) {
            try {
                jdbcTemplate.execute(indexSQL);
                created++;
                log.debug("Created {} index", type);
            } catch (Exception e) {
                if (e.getMessage().contains("already exists")) {
                    skipped++;
                    log.debug("Skipped existing {} index", type);
                } else {
                    log.warn("Failed to create {} index: {}", type, e.getMessage());
                }
            }
        }

        log.info("Processed {} indexes: {} created, {} skipped", type, created, skipped);
    }

    private void updateTableStatistics() {
        try {
            log.info("Updating table statistics...");
            jdbcTemplate.execute("ANALYZE products");
            jdbcTemplate.execute("ANALYZE manufacturers");
            jdbcTemplate.execute("ANALYZE categories");
            log.debug("Table statistics updated");
        } catch (Exception e) {
            log.warn("Failed to update table statistics: {}", e.getMessage());
        }
    }

    private void performanceTest() {
        try {
            log.info("Running performance test...");

            long startTime = System.nanoTime();
            Integer count = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*) FROM products p 
                            WHERE p.active = true
                            AND to_tsvector('simple', coalesce(p.name_bg, '') || ' ' || coalesce(p.model, '')) 
                                @@ plainto_tsquery('simple', 'test')
                            """,
                    Integer.class
            );
            long duration = (System.nanoTime() - startTime) / 1_000_000; // Convert to ms

            log.info("Performance test completed: {} results found in {}ms", count != null ? count : 0, duration);

            if (duration > 100) {
                log.warn("Search performance is slower than expected ({}ms). Consider checking indexes.", duration);
            } else {
                log.info("Search performance is excellent! ({}ms)", duration);
            }

        } catch (Exception e) {
            log.warn("Performance test failed: {}", e.getMessage());
        }
    }

    public void rebuildIndexes() {
        log.info("Rebuilding all search indexes...");
        try {
            dropExistingIndexes();

            initializeSearchIndexes();

        } catch (Exception e) {
            log.error("Failed to rebuild indexes: {}", e.getMessage(), e);
            throw new RuntimeException("Index rebuild failed", e);
        }
    }

    private void dropExistingIndexes() {
        String[] indexesToDrop = {
                "idx_products_search_fulltext",
                "idx_products_name_bg_trgm",
                "idx_products_name_en_trgm",
                "idx_products_final_price_filtered",
                "idx_products_created_at_filtered",
                "idx_products_featured_filtered",
                "idx_products_category_filtered",
                "idx_products_manufacturer_filtered",
                "idx_products_search_composite",
                "idx_products_active_show_flag",
                "idx_products_active",
                "idx_products_barcode"
        };

        for (String indexName : indexesToDrop) {
            try {
                jdbcTemplate.execute("DROP INDEX IF EXISTS " + indexName);
                log.debug("Dropped index: {}", indexName);
            } catch (Exception e) {
                log.debug("Could not drop index {}: {}", indexName, e.getMessage());
            }
        }
    }

    public Map<String, Object> getIndexStatistics() {
        try {
            List<Map<String, Object>> indexStats = jdbcTemplate.queryForList(
                    """
                            SELECT 
                                indexrelname as index_name,
                                idx_scan as scans,
                                idx_tup_read as tuples_read,
                                idx_tup_fetch as tuples_fetched,
                                pg_size_pretty(pg_relation_size(indexrelid)) as size
                            FROM pg_stat_user_indexes 
                            WHERE schemaname = 'public' 
                            AND indexrelname LIKE 'idx_%'
                            ORDER BY idx_scan DESC
                            """
            );

            return Map.of(
                    "indexes", indexStats,
                    "total_indexes", indexStats.size(),
                    "last_updated", System.currentTimeMillis()
            );

        } catch (Exception e) {
            log.error("Failed to get index statistics: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }
}