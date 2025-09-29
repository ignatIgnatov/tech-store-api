package com.techstore.controller;

import com.techstore.service.SearchIndexService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Hidden
@RestController
@RequestMapping("/api/admin/search")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin - Search Index", description = "Search index management operations")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class SearchIndexManagementController {

    private final SearchIndexService searchIndexService;

    @Operation(summary = "Rebuild entire search index")
    @PostMapping("/index/rebuild")
    public ResponseEntity<Map<String, String>> rebuildIndex() {
        log.info("Admin requested search index rebuild");

        searchIndexService.rebuildIndex();

        return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", "Search index rebuild started in background"
        ));
    }

    @Operation(summary = "Rebuild index for specific entity")
    @PostMapping("/index/rebuild/{entityType}")
    public ResponseEntity<Map<String, String>> rebuildIndexForEntity(
            @PathVariable String entityType) {

        log.info("Admin requested search index rebuild for entity: {}", entityType);

        try {
            Class<?> entityClass = Class.forName("com.techstore.entity." + entityType);
            searchIndexService.rebuildIndex(entityClass);

            return ResponseEntity.ok(Map.of(
                    "status", "started",
                    "message", "Search index rebuild started for " + entityType
            ));

        } catch (ClassNotFoundException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Entity type not found: " + entityType
            ));
        }
    }

    @Operation(summary = "Purge search index")
    @DeleteMapping("/index")
    public ResponseEntity<Map<String, String>> purgeIndex() {
        log.warn("Admin requested search index purge");

        try {
            searchIndexService.purgeIndex();

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Search index purged successfully"
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to purge index: " + e.getMessage()
            ));
        }
    }

    @Operation(summary = "Refresh search index")
    @PostMapping("/index/refresh")
    public ResponseEntity<Map<String, String>> refreshIndex() {
        log.info("Admin requested search index refresh");

        try {
            searchIndexService.refreshIndex();

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Search index refreshed successfully"
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to refresh index: " + e.getMessage()
            ));
        }
    }

    @Operation(summary = "Get search index statistics")
    @GetMapping("/index/stats")
    public ResponseEntity<SearchIndexService.SearchIndexStats> getIndexStats() {
        log.debug("Admin requested search index statistics");

        try {
            SearchIndexService.SearchIndexStats stats = searchIndexService.getIndexStats();
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Error getting index statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}