package com.techstore.controller;

import com.techstore.dto.AdvancedFilterRequestDTO;
import com.techstore.dto.ProductComparisonDTO;
import com.techstore.dto.ProductRequestDTO;
import com.techstore.dto.ProductResponseDTO;
import com.techstore.dto.ProductSummaryDTO;
import com.techstore.enums.ProductStatus;
import com.techstore.service.AdvancedFilteringService;
import com.techstore.service.ProductService;
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

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class ProductController {

    private final ProductService productService;
    private final AdvancedFilteringService filteringService;

    @PostMapping("/filter/advanced")
    public ResponseEntity<Page<ProductSummaryDTO>> filterProductsAdvanced(
            @RequestBody AdvancedFilterRequestDTO filterRequest,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ProductSummaryDTO> products = filteringService.filterProductsAdvanced(filterRequest, pageable);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/compare")
    public ResponseEntity<List<ProductComparisonDTO>> compareProducts(@RequestParam List<Long> productIds) {
        // Implementation for product comparison
        return ResponseEntity.ok(List.of());
    }

    @GetMapping
    public ResponseEntity<Page<ProductSummaryDTO>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ProductSummaryDTO> products = productService.getAllProducts(pageable);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> getProductById(@PathVariable Long id) {
        ProductResponseDTO product = productService.getProductById(id);
        return ResponseEntity.ok(product);
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<Page<ProductSummaryDTO>> getProductsByCategory(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ProductSummaryDTO> products = productService.getProductsByCategory(categoryId, pageable);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/brand/{brandId}")
    public ResponseEntity<Page<ProductSummaryDTO>> getProductsByBrand(
            @PathVariable Long brandId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ProductSummaryDTO> products = productService.getProductsByBrand(brandId, pageable);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/featured")
    public ResponseEntity<Page<ProductSummaryDTO>> getFeaturedProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ProductSummaryDTO> products = productService.getFeaturedProducts(pageable);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/on-sale")
    public ResponseEntity<Page<ProductSummaryDTO>> getProductsOnSale(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("discount").descending());
        Page<ProductSummaryDTO> products = productService.getProductsOnSale(pageable);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/search")
    public ResponseEntity<Page<ProductSummaryDTO>> searchProducts(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ProductSummaryDTO> products = productService.searchProducts(q, pageable);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/filter")
    public ResponseEntity<Page<ProductSummaryDTO>> filterProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean onSale,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        ProductStatus productStatus = ProductStatus.valueOf(status);

        Page<ProductSummaryDTO> products = productService.filterProducts(
                categoryId, brandId, minPrice, maxPrice, productStatus, onSale, q, pageable);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}/related")
    public ResponseEntity<List<ProductSummaryDTO>> getRelatedProducts(
            @PathVariable Long id,
            @RequestParam(defaultValue = "8") int limit) {

        List<ProductSummaryDTO> relatedProducts = productService.getRelatedProducts(id, limit);
        return ResponseEntity.ok(relatedProducts);
    }

    // ===== ADMIN ENDPOINTS =====

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ProductResponseDTO> createProduct(@Valid @RequestBody ProductRequestDTO requestDTO) {
        log.info("Creating product with SKU: {}", requestDTO.getSku());
        ProductResponseDTO createdProduct = productService.createProduct(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdProduct);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ProductResponseDTO> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequestDTO requestDTO) {

        log.info("Updating product with id: {}", id);
        ProductResponseDTO updatedProduct = productService.updateProduct(id, requestDTO);
        return ResponseEntity.ok(updatedProduct);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        log.info("Deleting product with id: {}", id);
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> permanentDeleteProduct(@PathVariable Long id) {
        log.info("Permanently deleting product with id: {}", id);
        productService.permanentDeleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    // ===== BULK OPERATIONS =====

    @PutMapping("/bulk/activate")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<String> activateProducts(@RequestBody List<Long> productIds) {
        // Implementation would be in service
        return ResponseEntity.ok("Products activated successfully");
    }

    @PutMapping("/bulk/deactivate")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<String> deactivateProducts(@RequestBody List<Long> productIds) {
        // Implementation would be in service
        return ResponseEntity.ok("Products deactivated successfully");
    }

    @PutMapping("/bulk/feature")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<String> featureProducts(@RequestBody List<Long> productIds) {
        // Implementation would be in service
        return ResponseEntity.ok("Products featured successfully");
    }

    @PutMapping("/bulk/unfeature")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<String> unfeatureProducts(@RequestBody List<Long> productIds) {
        // Implementation would be in service
        return ResponseEntity.ok("Products unfeatured successfully");
    }

    // ===== INVENTORY MANAGEMENT =====

    @PutMapping("/{id}/stock")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<String> updateStock(
            @PathVariable Long id,
            @RequestParam Integer quantity) {
        // Implementation would be in service
        return ResponseEntity.ok("Stock updated successfully");
    }

    @PutMapping("/{id}/discount")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<String> updateDiscount(
            @PathVariable Long id,
            @RequestParam BigDecimal discount) {
        // Implementation would be in service
        return ResponseEntity.ok("Discount updated successfully");
    }

    // ===== ANALYTICS ENDPOINTS =====

    @GetMapping("/analytics/low-stock")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<ProductSummaryDTO>> getLowStockProducts(
            @RequestParam(defaultValue = "10") int threshold) {
        // Implementation would be in service
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/analytics/out-of-stock")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<ProductSummaryDTO>> getOutOfStockProducts() {
        // Implementation would be in service
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/analytics/top-selling")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<ProductSummaryDTO>> getTopSellingProducts(
            @RequestParam(defaultValue = "10") int limit) {
        // Implementation would be in service (would need sales data)
        return ResponseEntity.ok(List.of());
    }
}