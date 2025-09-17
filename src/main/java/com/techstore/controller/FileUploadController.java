package com.techstore.controller;

import com.techstore.service.FileUploadService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Hidden
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @PostMapping("/products")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> uploadProductImage(@RequestParam("file") MultipartFile file) {
        log.info("Uploading product image: {}", file.getOriginalFilename());
        String filePath = fileUploadService.uploadFile(file, "products");
        return ResponseEntity.ok(Map.of("url", filePath));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> uploadCategoryImage(@RequestParam("file") MultipartFile file) {
        log.info("Uploading category image: {}", file.getOriginalFilename());
        String filePath = fileUploadService.uploadFile(file, "categories");
        return ResponseEntity.ok(Map.of("url", filePath));
    }

    @PostMapping("/brands")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> uploadBrandLogo(@RequestParam("file") MultipartFile file) {
        log.info("Uploading brand logo: {}", file.getOriginalFilename());
        String filePath = fileUploadService.uploadFile(file, "brands");
        return ResponseEntity.ok(Map.of("url", filePath));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<String> deleteFile(@RequestParam("path") String filePath) {
        log.info("Deleting file: {}", filePath);
        fileUploadService.deleteFile(filePath);
        return ResponseEntity.ok("File deleted successfully");
    }
}