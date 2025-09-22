package com.techstore.controller;

import com.techstore.service.SyncService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdminController {

    private final SyncService syncService;

    @Hidden
    @PostMapping("/sync/categories")
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> syncCategories() {
        try {
            syncService.syncCategories();
            return ResponseEntity.ok("Categories synchronization completed successfully");
        } catch (Exception e) {
            log.error("Error during manual categories synchronization", e);
            return ResponseEntity.internalServerError().body("Error during synchronization: " + e.getMessage());
        }
    }

    @Hidden
    @PostMapping("/sync/manufacturers")
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> syncManufacturers() {
        try {
            syncService.syncManufacturers();
            return ResponseEntity.ok("Manufacturers synchronization completed successfully");
        } catch (Exception e) {
            log.error("Error during manual manufacturers synchronization", e);
            return ResponseEntity.internalServerError().body("Error during synchronization: " + e.getMessage());
        }
    }

    @Hidden
    @PostMapping("/sync/parameters")
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> syncParameters() {
        try {
            syncService.syncParameters();
            return ResponseEntity.ok("Parameters synchronization completed successfully");
        } catch (Exception e) {
            log.error("Error during manual parameters synchronization", e);
            return ResponseEntity.internalServerError().body("Error during synchronization: " + e.getMessage());
        }
    }

    @Hidden
    @PostMapping("/sync/products")
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> syncProducts() {
        try {
            syncService.syncProducts();
            return ResponseEntity.ok("Products synchronization completed successfully");
        } catch (Exception e) {
            log.error("Error during manual products synchronization", e);
            return ResponseEntity.internalServerError().body("Error during synchronization: " + e.getMessage());
        }
    }

    @Hidden
    @PostMapping("/sync/products-by-category")
    public ResponseEntity<String> syncProductsByCat(@RequestParam("id") Long id) {
        try {
            syncService.syncProductsByCategory(id);
            return ResponseEntity.ok("Products synchronization completed successfully");
        } catch (Exception e) {
            log.error("Error during manual products synchronization", e);
            return ResponseEntity.internalServerError().body("Error during synchronization: " + e.getMessage());
        }
    }

    @Hidden
    @PostMapping("/sync/documents")
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> syncDocuments() {
        try {
            syncService.syncDocuments();
            return ResponseEntity.ok("Documents synchronization completed successfully");
        } catch (Exception e) {
            log.error("Error during manual documents synchronization", e);
            return ResponseEntity.internalServerError().body("Error during synchronization: " + e.getMessage());
        }
    }

    @Hidden
    @PostMapping("/sync/documents-by-product")
    public ResponseEntity<String> syncDocumentsByProduct(@RequestParam("id") Long productId) {
        try {
            syncService.syncDocumentsByProduct(productId);
            return ResponseEntity.ok("Documents synchronization for product completed successfully");
        } catch (Exception e) {
            log.error("Error during manual documents synchronization for product {}", productId, e);
            return ResponseEntity.internalServerError().body("Error during synchronization: " + e.getMessage());
        }
    }

    @Hidden
    @PostMapping("/sync/all")
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> syncAll() {
        try {
            syncService.fetchAll();
            return ResponseEntity.ok("Full synchronization completed successfully");
        } catch (Exception e) {
            log.error("Error during manual full synchronization", e);
            return ResponseEntity.internalServerError().body("Error during synchronization: " + e.getMessage());
        }
    }

    // Add these methods to your existing AdminController class

    /**
     * Get Wildlife Surveillance products count from Tekra
     */
    @Hidden
    @GetMapping("/tekra/wildlife-count")
    public ResponseEntity<Map<String, Object>> getWildlifeSurveillanceCount() {
        try {
            int count = syncService.getTekraVideoSurveillanceProductsCount();

            Map<String, Object> response = new HashMap<>();
            response.put("count", count);
            response.put("message", String.format("Found %d Wildlife Surveillance products in Tekra", count));
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting Wildlife Surveillance count", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get count: " + e.getMessage()));
        }
    }

    @Hidden
    @PostMapping("/sync/tekra/wildlife-surveillance")
    public ResponseEntity<String> syncWildlifeSurveillance() {
        try {
            syncService.syncVideoSurveillanceOnly();
            return ResponseEntity.ok("Wildlife Surveillance synchronization from Tekra completed successfully");
        } catch (Exception e) {
            log.error("Error during Wildlife Surveillance synchronization from Tekra", e);
            return ResponseEntity.internalServerError()
                    .body("Error during synchronization: " + e.getMessage());
        }
    }

    @Hidden
    @PostMapping("/sync/tekra/manufacturers")
    public ResponseEntity<String> syncTekraManufacturers() {
        try {
            syncService.syncTekraManufacturers();
            return ResponseEntity.ok("Tekra manufacturers synchronization completed successfully");
        } catch (Exception e) {
            log.error("Error during Tekra manufacturers synchronization", e);
            return ResponseEntity.internalServerError()
                    .body("Error during synchronization: " + e.getMessage());
        }
    }

    @Hidden
    @PostMapping("/sync/tekra/categories")
    public ResponseEntity<String> syncTekraCategories() {
        try {
            syncService.syncTekraCategories();
            return ResponseEntity.ok("Tekra categories synchronization completed successfully");
        } catch (Exception e) {
            log.error("Error during Tekra categories synchronization", e);
            return ResponseEntity.internalServerError()
                    .body("Error during synchronization: " + e.getMessage());
        }
    }

    /**
     * Sync only Tekra parameters
     */
    @Hidden
    @PostMapping("/sync/tekra/parameters")
    public ResponseEntity<String> syncTekraParameters() {
        try {
            syncService.syncTekraParameters();
            return ResponseEntity.ok("Tekra parameters synchronization completed successfully");
        } catch (Exception e) {
            log.error("Error during Tekra parameters synchronization", e);
            return ResponseEntity.internalServerError()
                    .body("Error during synchronization: " + e.getMessage());
        }
    }

    @Hidden
    @PostMapping("/sync/tekra/products")
    public ResponseEntity<String> syncTekraProducts() {
        try {
            syncService.syncTekraProducts();
            return ResponseEntity.ok("Tekra products synchronization completed successfully");
        } catch (Exception e) {
            log.error("Error during Tekra products synchronization", e);
            return ResponseEntity.internalServerError()
                    .body("Error during synchronization: " + e.getMessage());
        }
    }

    @Hidden
    @PostMapping("/sync/tekra/full")
    public ResponseEntity<Map<String, Object>> fullTekraSync() {
        Map<String, Object> result = new HashMap<>();

        try {

            int productCount = syncService.getTekraVideoSurveillanceProductsCount();
            result.put("productsToSync", productCount);

            syncService.syncVideoSurveillanceOnly();

            result.put("success", true);
            result.put("message", String.format("Successfully synced %d Video Surveillance products from Tekra", productCount));
            result.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Full Tekra sync failed", e);
            result.put("success", false);
            result.put("message", "Sync failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
}