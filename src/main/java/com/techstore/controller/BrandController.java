package com.techstore.controller;

import com.techstore.dto.BrandRequestDTO;
import com.techstore.dto.BrandResponseDTO;
import com.techstore.service.BrandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class BrandController {

    private final BrandService brandService;

    // ===== PUBLIC ENDPOINTS =====

    @GetMapping
    public ResponseEntity<List<BrandResponseDTO>> getAllBrands() {
        List<BrandResponseDTO> brands = brandService.getAllBrands();
        return ResponseEntity.ok(brands);
    }

    @GetMapping("/paginated")
    public ResponseEntity<Page<BrandResponseDTO>> getAllBrandsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "sortOrder") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<BrandResponseDTO> brands = brandService.getAllBrands(pageable);
        return ResponseEntity.ok(brands);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BrandResponseDTO> getBrandById(@PathVariable Long id) {
        BrandResponseDTO brand = brandService.getBrandById(id);
        return ResponseEntity.ok(brand);
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<BrandResponseDTO> getBrandBySlug(@PathVariable String slug) {
        BrandResponseDTO brand = brandService.getBrandBySlug(slug);
        return ResponseEntity.ok(brand);
    }

    @GetMapping("/featured")
    public ResponseEntity<List<BrandResponseDTO>> getFeaturedBrands() {
        List<BrandResponseDTO> featuredBrands = brandService.getFeaturedBrands();
        return ResponseEntity.ok(featuredBrands);
    }

    // ===== ADMIN ENDPOINTS =====

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<BrandResponseDTO> createBrand(@Valid @RequestBody BrandRequestDTO requestDTO) {
        log.info("Creating brand with slug: {}", requestDTO.getSlug());
        BrandResponseDTO createdBrand = brandService.createBrand(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdBrand);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<BrandResponseDTO> updateBrand(
            @PathVariable Long id,
            @Valid @RequestBody BrandRequestDTO requestDTO) {

        log.info("Updating brand with id: {}", id);
        BrandResponseDTO updatedBrand = brandService.updateBrand(id, requestDTO);
        return ResponseEntity.ok(updatedBrand);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteBrand(@PathVariable Long id) {
        log.info("Deleting brand with id: {}", id);
        brandService.deleteBrand(id);
        return ResponseEntity.noContent().build();
    }
}