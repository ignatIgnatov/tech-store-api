package com.techstore.service;

import com.techstore.entity.Product;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.massindexing.MassIndexer;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchIndexService {

    private final EntityManager entityManager;

    /**
     * Rebuild the entire search index
     */
    @Async
    @Transactional
    public CompletableFuture<Void> rebuildIndex() {
        log.info("Starting search index rebuild...");

        try {
            SearchSession searchSession = Search.session(entityManager);

            MassIndexer indexer = searchSession.massIndexer(Product.class)
                    .threadsToLoadObjects(5)
                    .batchSizeToLoadObjects(25)
                    .batchSizeToLoadObjects(100);

            indexer.startAndWait();

            log.info("Search index rebuild completed successfully");
            return CompletableFuture.completedFuture(null);

        } catch (InterruptedException e) {
            log.error("Search index rebuild was interrupted", e);
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(e);
        } catch (Exception e) {
            log.error("Error rebuilding search index", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Rebuild index for specific entity type
     */
    @Async
    @Transactional
    public CompletableFuture<Void> rebuildIndex(Class<?> entityClass) {
        log.info("Starting search index rebuild for entity: {}", entityClass.getSimpleName());

        try {
            SearchSession searchSession = Search.session(entityManager);

            MassIndexer indexer = searchSession.massIndexer(entityClass)
                    .threadsToLoadObjects(3)
                    .batchSizeToLoadObjects(25);

            indexer.startAndWait();

            log.info("Search index rebuild completed for entity: {}", entityClass.getSimpleName());
            return CompletableFuture.completedFuture(null);

        } catch (InterruptedException e) {
            log.error("Search index rebuild was interrupted for entity: {}", entityClass.getSimpleName(), e);
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(e);
        } catch (Exception e) {
            log.error("Error rebuilding search index for entity: {}", entityClass.getSimpleName(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Check if search index is empty and rebuild if necessary
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeSearchIndex() {
        log.info("Checking search index status...");

        try {
            SearchSession searchSession = Search.session(entityManager);

            // Check if index has any products
            long productCount = searchSession.search(Product.class)
                    .where(f -> f.matchAll())
                    .fetchTotalHitCount();

            if (productCount == 0) {
                log.info("Search index is empty, triggering rebuild...");
                rebuildIndex();
            } else {
                log.info("Search index contains {} products", productCount);
            }

        } catch (Exception e) {
            log.warn("Could not check search index status, will rebuild: {}", e.getMessage());
            rebuildIndex();
        }
    }

    /**
     * Purge all documents from the index
     */
    @Transactional
    public void purgeIndex() {
        log.warn("Purging search index...");

        try {
            SearchSession searchSession = Search.session(entityManager);
            searchSession.workspace(Product.class).purge();
            searchSession.workspace(Product.class).refresh();

            log.info("Search index purged successfully");

        } catch (Exception e) {
            log.error("Error purging search index", e);
            throw new RuntimeException("Failed to purge search index", e);
        }
    }

    /**
     * Refresh the search index
     */
    @Transactional
    public void refreshIndex() {
        log.debug("Refreshing search index...");

        try {
            SearchSession searchSession = Search.session(entityManager);
            searchSession.workspace(Product.class).refresh();

            log.debug("Search index refreshed successfully");

        } catch (Exception e) {
            log.error("Error refreshing search index", e);
            throw new RuntimeException("Failed to refresh search index", e);
        }
    }

    /**
     * Get index statistics
     */
    @Transactional(readOnly = true)
    public SearchIndexStats getIndexStats() {
        try {
            SearchSession searchSession = Search.session(entityManager);

            long totalProducts = searchSession.search(Product.class)
                    .where(f -> f.matchAll())
                    .fetchTotalHitCount();

            long activeProducts = searchSession.search(Product.class)
                    .where(f -> f.match().field("active").matching(true))
                    .fetchTotalHitCount();

            long visibleProducts = searchSession.search(Product.class)
                    .where(f -> f.bool(b -> {
                        b.must(f.match().field("active").matching(true));
                        b.must(f.match().field("show").matching(true));
                    }))
                    .fetchTotalHitCount();

            long featuredProducts = searchSession.search(Product.class)
                    .where(f -> f.match().field("featured").matching(true))
                    .fetchTotalHitCount();

            long productsOnSale = searchSession.search(Product.class)
                    .where(f -> f.match().field("onSale").matching(true))
                    .fetchTotalHitCount();

            return SearchIndexStats.builder()
                    .totalProducts(totalProducts)
                    .activeProducts(activeProducts)
                    .visibleProducts(visibleProducts)
                    .featuredProducts(featuredProducts)
                    .productsOnSale(productsOnSale)
                    .lastUpdated(java.time.LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Error getting index statistics", e);
            throw new RuntimeException("Failed to get index statistics", e);
        }
    }

    // DTO for index statistics
    @lombok.Data
    @lombok.Builder
    public static class SearchIndexStats {
        private long totalProducts;
        private long activeProducts;
        private long visibleProducts;
        private long featuredProducts;
        private long productsOnSale;
        private java.time.LocalDateTime lastUpdated;
    }
}