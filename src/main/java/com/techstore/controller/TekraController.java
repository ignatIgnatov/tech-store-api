package com.techstore.controller;

import com.techstore.dto.tekra.TekraSyncResponse;
import com.techstore.service.SyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/tekra")
@RequiredArgsConstructor
@Slf4j
//@PreAuthorize("hasRole('ADMIN')")
public class TekraController {

    private final SyncService syncService;

    @PostMapping("/sync/categories")
    public ResponseEntity<String> syncTekraCategories() {
        try {
            log.info("Starting Tekra categories synchronization via API");
            syncService.syncTekraCategories();
            return ResponseEntity.ok("Tekra categories synchronization completed successfully");
        } catch (Exception e) {
            log.error("Tekra categories synchronization failed", e);
            return ResponseEntity.internalServerError()
                    .body("Tekra categories synchronization failed: " + e.getMessage());
        }
    }

    @PostMapping("/sync/products")
    public ResponseEntity<String> syncTekraProducts() {
        try {
            log.info("Starting Tekra products synchronization via API");
            syncService.syncTekraProducts();
            return ResponseEntity.ok("Tekra products synchronization completed successfully");
        } catch (Exception e) {
            log.error("Tekra products synchronization failed", e);
            return ResponseEntity.internalServerError()
                    .body("Tekra products synchronization failed: " + e.getMessage());
        }
    }

    // Add these endpoints to your TekraController.java

// ============ MANUFACTURERS SYNC ENDPOINTS ============

    @PostMapping("/sync/manufacturers")
    public ResponseEntity<TekraSyncResponse> syncTekraManufacturers() {
        TekraSyncResponse response = new TekraSyncResponse();
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting Tekra manufacturers synchronization via API");
            syncService.syncTekraManufacturers();

            response.setSuccess(true);
            response.setMessage("Tekra manufacturers synchronization completed successfully");
            response.setDurationMs(System.currentTimeMillis() - startTime);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Tekra manufacturers synchronization failed", e);
            response.setSuccess(false);
            response.setMessage("Tekra manufacturers synchronization failed: " + e.getMessage());
            response.setDurationMs(System.currentTimeMillis() - startTime);
            return ResponseEntity.internalServerError().body(response);
        }
    }

// ============ PARAMETERS SYNC ENDPOINTS ============

    @PostMapping("/sync/parameters")
    public ResponseEntity<TekraSyncResponse> syncTekraParameters() {
        TekraSyncResponse response = new TekraSyncResponse();
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting Tekra parameters synchronization via API");
            syncService.syncTekraParameters();

            response.setSuccess(true);
            response.setMessage("Tekra parameters synchronization completed successfully");
            response.setDurationMs(System.currentTimeMillis() - startTime);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Tekra parameters synchronization failed", e);
            response.setSuccess(false);
            response.setMessage("Tekra parameters synchronization failed: " + e.getMessage());
            response.setDurationMs(System.currentTimeMillis() - startTime);
            return ResponseEntity.internalServerError().body(response);
        }
    }

// ============ COMPLETE SYNC ENDPOINT ============

    @PostMapping("/sync/complete")
    public ResponseEntity<TekraSyncResponse> syncTekraComplete() {
        TekraSyncResponse response = new TekraSyncResponse();
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting complete Tekra synchronization (categories + manufacturers + parameters + products)");
            syncService.syncTekraComplete();

            response.setSuccess(true);
            response.setMessage("Complete Tekra synchronization finished successfully");
            response.setDurationMs(System.currentTimeMillis() - startTime);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Complete Tekra synchronization failed", e);
            response.setSuccess(false);
            response.setMessage("Complete Tekra synchronization failed: " + e.getMessage());
            response.setDurationMs(System.currentTimeMillis() - startTime);
            return ResponseEntity.internalServerError().body(response);
        }
    }
}