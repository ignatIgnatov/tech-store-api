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

    @Value("${app.search.postgresql.enable-trigram:true}")
    private boolean enableTrigram;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeSearchIndexes() {
        if (!autoCreateIndexes) {
            log.info("Auto-creation of search indexes is disabled");
            return;
        }

        try {
            log.info("Starting performance search indexes initialization...");
            long startTime = System.currentTimeMillis();

            validatePostgreSQLCapabilities();

            if (enableTrigram) {
                enableTrigramExtension();
                createTrigramIndexes();
            }

            createFilteringIndexes();
            createCompositeIndexes();
            updateTableStatistics();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Performance search indexes initialization completed in {}ms", duration);

            performanceTest();

        } catch (Exception e) {
            log.error("Failed to initialize search indexes: {}", e.getMessage(), e);
        }
    }

    private void validatePostgreSQLCapabilities() {
        try {
            jdbcTemplate.queryForObject(
                    "SELECT to_tsvector('simple', 'test')", String.class);
            log.debug("✓ Full-text search capabilities confirmed");

        } catch (Exception e) {
            log.warn("PostgreSQL full-text search capabilities not available: {}", e.getMessage());
        }
    }

    private void enableTrigramExtension() {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
            log.info("✓ pg_trgm extension enabled");

            // Тествай дали работи
            jdbcTemplate.queryForObject("SELECT similarity('test', 'test')", Float.class);
            log.debug("✓ Trigram search capabilities confirmed");

        } catch (Exception e) {
            log.warn("Could not enable pg_trgm extension: {}. Trigram search will be disabled.",
                    e.getMessage());
            enableTrigram = false;
        }
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
            CREATE INDEX IF NOT EXISTS idx_products_model_trgm 
            ON products USING gin(model gin_trgm_ops) 
            WHERE active = true AND model IS NOT NULL
            """,

                """
            CREATE INDEX IF NOT EXISTS idx_manufacturers_name_trgm 
            ON manufacturers USING gin(name gin_trgm_ops)
            """,

                """
            CREATE INDEX IF NOT EXISTS idx_categories_name_bg_trgm 
            ON categories USING gin(name_bg gin_trgm_ops)
            """
        };

        executeIndexes(indexes, "trigram");
    }

    private void createFilteringIndexes() {
        log.info("Creating filtered indexes for performance...");

        String[] indexes = {
                """
            CREATE INDEX IF NOT EXISTS idx_products_final_price_filtered 
            ON products(final_price) WHERE active = true AND show_flag = true
            """,

                """
            CREATE INDEX IF NOT EXISTS idx_products_created_at_filtered 
            ON products(created_at DESC) WHERE active = true AND show_flag = true
            """,

                """
            CREATE INDEX IF NOT EXISTS idx_products_featured_filtered
            ON products(featured DESC, created_at DESC) 
            WHERE active = true AND featured = true
            """,

                """
            CREATE INDEX IF NOT EXISTS idx_products_category_active
            ON products(category_id, final_price) 
            WHERE active = true AND show_flag = true
            """,

                """
            CREATE INDEX IF NOT EXISTS idx_products_manufacturer_active
            ON products(manufacturer_id, final_price) 
            WHERE active = true AND show_flag = true
            """,

                """
            CREATE INDEX IF NOT EXISTS idx_products_discount_filtered
            ON products(discount DESC, final_price ASC) 
            WHERE active = true AND discount > 0
            """
        };

        executeIndexes(indexes, "filtering");
    }

    private void createCompositeIndexes() {
        log.info("Creating composite indexes for complex queries...");

        String[] indexes = {
                """
            CREATE INDEX IF NOT EXISTS idx_products_search_composite 
            ON products(category_id, active, show_flag, final_price ASC, created_at DESC) 
            WHERE active = true
            """,

                """
            CREATE INDEX IF NOT EXISTS idx_products_featured_composite
            ON products(featured DESC, category_id, final_price ASC)
            WHERE active = true AND featured = true
            """,

                """
            CREATE INDEX IF NOT EXISTS idx_product_parameters_lookup
            ON product_parameters(product_id, parameter_id, parameter_option_id)
            """
        };

        executeIndexes(indexes, "composite");
    }

    private void executeIndexes(String[] indexes, String type) {
        int created = 0;
        int skipped = 0;
        int failed = 0;

        for (String indexSQL : indexes) {
            try {
                jdbcTemplate.execute(indexSQL);
                created++;
                log.debug("✓ Created {} index", type);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                    skipped++;
                    log.debug("- Skipped existing {} index", type);
                } else {
                    failed++;
                    log.warn("✗ Failed to create {} index: {}", type, e.getMessage());
                }
            }
        }

        if (created > 0 || failed > 0) {
            log.info("Processed {} {} indexes: {} created, {} skipped, {} failed",
                    type, indexes.length, created, skipped, failed);
        }
    }

    private void updateTableStatistics() {
        try {
            log.info("Updating table statistics for query optimization...");
            jdbcTemplate.execute("ANALYZE products");
            jdbcTemplate.execute("ANALYZE categories");
            jdbcTemplate.execute("ANALYZE manufacturers");
            jdbcTemplate.execute("ANALYZE product_parameters");
            log.debug("✓ Table statistics updated");
        } catch (Exception e) {
            log.warn("Failed to update table statistics: {}", e.getMessage());
        }
    }

    private void performanceTest() {
        try {
            log.info("Running search performance test...");

            // Test 1: Basic full-text search (uses idx_products_search_basic from migration)
            long startTime = System.nanoTime();
            Integer ftCount = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*) FROM products p 
                            WHERE p.active = true
                            AND to_tsvector('simple', coalesce(p.name_bg, '') || ' ' || coalesce(p.model, '')) 
                                @@ plainto_tsquery('simple', 'камера')
                            """,
                    Integer.class
            );
            long ftDuration = (System.nanoTime() - startTime) / 1_000_000;

            // Test 2: Trigram similarity search (if enabled)
            Long trgmDuration = null;
            if (enableTrigram) {
                try {
                    startTime = System.nanoTime();
                    Integer trgmCount = jdbcTemplate.queryForObject(
                            """
                                    SELECT COUNT(*) FROM products p 
                                    WHERE p.active = true
                                    AND p.name_bg % 'камер'
                                    LIMIT 100
                                    """,
                            Integer.class
                    );
                    trgmDuration = (System.nanoTime() - startTime) / 1_000_000;
                    log.info("✓ Trigram search test: {} results in {}ms", trgmCount, trgmDuration);
                } catch (Exception e) {
                    log.debug("Trigram test skipped: {}", e.getMessage());
                }
            }

            log.info("✓ Full-text search test: {} results in {}ms", ftCount, ftDuration);

            if (ftDuration > 300) {
                log.warn("⚠ Search performance is slower than expected ({}ms). Consider checking indexes.",
                        ftDuration);
            } else {
                log.info("✓ Search performance is excellent! ({}ms)", ftDuration);
            }

        } catch (Exception e) {
            log.warn("Performance test failed: {}", e.getMessage());
        }
    }

    public void rebuildPerformanceIndexes() {
        log.info("Rebuilding performance search indexes...");
        try {
            dropPerformanceIndexes();

            if (enableTrigram) {
                createTrigramIndexes();
            }
            createFilteringIndexes();
            createCompositeIndexes();
            updateTableStatistics();

            log.info("✓ Performance indexes rebuilt successfully");

        } catch (Exception e) {
            log.error("Failed to rebuild performance indexes: {}", e.getMessage(), e);
            throw new RuntimeException("Index rebuild failed", e);
        }
    }

    private void dropPerformanceIndexes() {
        String[] indexesToDrop = {
                // Trigram indexes
                "idx_products_name_bg_trgm",
                "idx_products_name_en_trgm",
                "idx_products_model_trgm",
                "idx_manufacturers_name_trgm",
                "idx_categories_name_bg_trgm",

                // Filtered indexes
                "idx_products_final_price_filtered",
                "idx_products_created_at_filtered",
                "idx_products_featured_filtered",
                "idx_products_category_active",
                "idx_products_manufacturer_active",
                "idx_products_discount_filtered",

                // Composite indexes
                "idx_products_search_composite",
                "idx_products_featured_composite",
                "idx_product_parameters_lookup"
        };

        int dropped = 0;
        for (String indexName : indexesToDrop) {
            try {
                jdbcTemplate.execute("DROP INDEX IF EXISTS " + indexName);
                dropped++;
                log.debug("✓ Dropped performance index: {}", indexName);
            } catch (Exception e) {
                log.debug("Could not drop index {}: {}", indexName, e.getMessage());
            }
        }

        if (dropped > 0) {
            log.info("Dropped {} performance indexes", dropped);
        }
    }

    public Map<String, Object> getIndexStatistics() {
        try {
            List<Map<String, Object>> indexStats = jdbcTemplate.queryForList(
                    """
                            SELECT 
                                schemaname,
                                tablename,
                                indexname as index_name,
                                idx_scan as scans,
                                idx_tup_read as tuples_read,
                                idx_tup_fetch as tuples_fetched,
                                pg_size_pretty(pg_relation_size(indexrelid)) as size
                            FROM pg_stat_user_indexes 
                            WHERE schemaname = 'public' 
                            ORDER BY idx_scan DESC
                            """
            );

            long totalScans = indexStats.stream()
                    .mapToLong(m -> ((Number) m.getOrDefault("scans", 0L)).longValue())
                    .sum();

            return Map.of(
                    "indexes", indexStats,
                    "total_indexes", indexStats.size(),
                    "total_scans", totalScans,
                    "trigram_enabled", enableTrigram,
                    "last_updated", System.currentTimeMillis()
            );

        } catch (Exception e) {
            log.error("Failed to get index statistics: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    public Map<String, Object> checkIndexHealth() {
        try {
            // Проверка за missing indexes
            List<Map<String, Object>> missingIndexes = jdbcTemplate.queryForList(
                    """
                            SELECT 
                                schemaname,
                                tablename,
                                attname as column_name,
                                n_distinct,
                                correlation
                            FROM pg_stats 
                            WHERE schemaname = 'public' 
                            AND tablename IN ('products', 'categories', 'manufacturers')
                            AND n_distinct > 100
                            ORDER BY n_distinct DESC
                            LIMIT 10
                            """
            );

            // Проверка за bloated indexes
            List<Map<String, Object>> bloatedIndexes = jdbcTemplate.queryForList(
                    """
                            SELECT 
                                schemaname,
                                tablename,
                                indexname,
                                pg_size_pretty(pg_relation_size(indexrelid)) as size
                            FROM pg_stat_user_indexes
                            WHERE schemaname = 'public'
                            AND pg_relation_size(indexrelid) > 1000000
                            ORDER BY pg_relation_size(indexrelid) DESC
                            LIMIT 5
                            """
            );

            return Map.of(
                    "high_cardinality_columns", missingIndexes,
                    "large_indexes", bloatedIndexes,
                    "recommendation", missingIndexes.isEmpty() ?
                            "All indexes look good" :
                            "Consider adding indexes on high cardinality columns"
            );

        } catch (Exception e) {
            log.error("Failed to check index health: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }
}