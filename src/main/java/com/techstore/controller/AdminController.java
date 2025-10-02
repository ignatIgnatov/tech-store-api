package com.techstore.controller;

import com.techstore.service.SyncService;
import com.techstore.service.TekraApiService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Hidden
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdminController {

    private final SyncService syncService;
    private final TekraApiService tekraApiService;

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

    @GetMapping("/admin/debug-category-paths")
    public String debugPaths() {
        syncService.debugCategoryPathMatching();
        return "Check logs";
    }

    @GetMapping("/admin/analyze-product-categories")
    public String analyzeProductCategories() {
        List<Map<String, Object>> allProducts = new ArrayList<>();

        // Fetch products under "videonablyudenie"
        List<Map<String, Object>> products = tekraApiService.getProductsRaw("videonablyudenie");
        allProducts.addAll(products);

        // Group by category_1
        Map<String, Long> category1Counts = allProducts.stream()
                .collect(Collectors.groupingBy(
                        p -> {
                            String cat1 = getString(p, "category_1");
                            return cat1 != null ? cat1 : "NULL";
                        },
                        Collectors.counting()
                ));

        log.info("=== PRODUCTS UNDER videonablyudenie ===");
        log.info("Total products: {}", allProducts.size());
        log.info("Breakdown by category_1:");
        category1Counts.forEach((cat, count) ->
                log.info("  - '{}': {} products", cat, count)
        );

        return "Check logs";
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
    }
}