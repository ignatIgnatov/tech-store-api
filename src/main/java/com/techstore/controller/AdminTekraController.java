package com.techstore.controller;

import com.techstore.service.SyncService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Hidden
@RestController
@RequestMapping("/admin/tekra/sync")
@RequiredArgsConstructor
@Slf4j
//@PreAuthorize("hasRole('ADMIN')")
public class AdminTekraController {

    private final SyncService syncService;

    @PostMapping(value = "/categories")
    public ResponseEntity<Map<String, Object>> syncCategories() {
        try {
            long startTime = System.currentTimeMillis();
            syncService.syncTekraCategories();
            long duration = System.currentTimeMillis() - startTime;

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Tekra categories synchronization completed",
                    "duration", duration + "ms"
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during Tekra categories sync", e);
            Map<String, Object> response = Map.of(
                    "success", false,
                    "message", "Error: " + e.getMessage()
            );
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping(value = "/manufacturers")
    public ResponseEntity<Map<String, Object>> syncManufacturers() {
        try {
            long startTime = System.currentTimeMillis();
            syncService.syncTekraManufacturers();
            long duration = System.currentTimeMillis() - startTime;

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Tekra manufacturers synchronization completed",
                    "duration", duration + "ms"
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during Tekra manufacturers sync", e);
            Map<String, Object> response = Map.of(
                    "success", false,
                    "message", "Error: " + e.getMessage()
            );
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping(value = "/parameters")
    public ResponseEntity<Map<String, Object>> syncParameters() {
        try {
            long startTime = System.currentTimeMillis();
            syncService.syncTekraParameters();
            long duration = System.currentTimeMillis() - startTime;

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Tekra parameters synchronization completed",
                    "duration", duration + "ms"
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during Tekra parameters sync", e);
            Map<String, Object> response = Map.of(
                    "success", false,
                    "message", "Error: " + e.getMessage()
            );
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping(value = "/products")
    public ResponseEntity<Map<String, Object>> syncProducts() {
        try {
            long startTime = System.currentTimeMillis();
            syncService.syncTekraProducts();
            long duration = System.currentTimeMillis() - startTime;

            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Tekra products synchronization completed",
                    "duration", duration + "ms"
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error during Tekra products sync", e);
            Map<String, Object> response = Map.of(
                    "success", false,
                    "message", "Error: " + e.getMessage()
            );
            return ResponseEntity.status(500).body(response);
        }
    }
}