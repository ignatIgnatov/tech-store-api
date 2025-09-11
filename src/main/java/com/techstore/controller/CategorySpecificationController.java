package com.techstore.controller;

import com.techstore.dto.CategoryFilterDTO;
import com.techstore.dto.CategorySpecificationTemplateDTO;
import com.techstore.service.CategorySpecificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/category-specifications")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class CategorySpecificationController {

    private final CategorySpecificationService specificationService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<CategorySpecificationTemplateDTO> createTemplate(@Valid @RequestBody CategorySpecificationTemplateDTO dto) {
        CategorySpecificationTemplateDTO created = specificationService.createTemplate(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<CategorySpecificationTemplateDTO>> getCategoryTemplates(@PathVariable Long categoryId) {
        List<CategorySpecificationTemplateDTO> templates = specificationService.getCategoryTemplates(categoryId);
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/category/{categoryId}/filters")
    public ResponseEntity<CategoryFilterDTO> getCategoryFilters(@PathVariable Long categoryId) {
        CategoryFilterDTO filters = specificationService.getCategoryFilters(categoryId);
        return ResponseEntity.ok(filters);
    }
}
