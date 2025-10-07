package com.techstore.controller;

import com.techstore.service.SyncService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/admin/vali/sync")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdminValiController {

    private final SyncService syncService;

    @PostMapping("/categories")
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

    @PostMapping("/manufacturers")
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

    @PostMapping("/parameters")
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

    @PostMapping("/products")
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

//    @GetMapping("/sync/duplicates")
//    public ResponseEntity<Map<String, Object>> getDuplicatesStats() {
//        Map<String, Object> response = syncService.getDuplicationStats();
//        return ResponseEntity.ok(response);
//    }
}