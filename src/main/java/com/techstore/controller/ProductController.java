package com.techstore.controller;

import com.techstore.dto.ProductResponseDTO;
import com.techstore.dto.filter.AdvancedFilterRequestDTO;
import com.techstore.dto.request.ProductCreateRequestDTO;
import com.techstore.dto.request.ProductImageOperationsDTO;
import com.techstore.dto.request.ProductImageUpdateDTO;
import com.techstore.dto.request.ProductUpdateRequestDTO;
import com.techstore.dto.response.ProductImageUploadResponseDTO;
import com.techstore.enums.ProductStatus;
import com.techstore.service.FilteringService;
import com.techstore.service.ProductService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Products", description = "Product management APIs")
public class ProductController {

    private final ProductService productService;
    private final FilteringService filteringService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get all products", description = "Retrieve paginated list of active products")
    public ResponseEntity<Page<ProductResponseDTO>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "en") String language) {

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ProductResponseDTO> products = productService.getAllProducts(pageable, language);
        return ResponseEntity.ok(products);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get product by ID", description = "Retrieve detailed product information")
    public ResponseEntity<ProductResponseDTO> getProductById(@PathVariable Long id,  @RequestParam(defaultValue = "en") String language) {
        ProductResponseDTO product = productService.getProductById(id, language);
        return ResponseEntity.ok(product);
    }

    @GetMapping(value = "/category/{categoryId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get products by category", description = "Retrieve products filtered by category")
    public ResponseEntity<Page<ProductResponseDTO>> getProductsByCategory(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "en") String language) {

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ProductResponseDTO> products = productService.getProductsByCategory(categoryId, pageable, language);
        return ResponseEntity.ok(products);
    }

    @GetMapping(value = "/brand/{brandId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get products by brand", description = "Retrieve products filtered by manufacturer/brand")
    public ResponseEntity<Page<ProductResponseDTO>> getProductsByBrand(
            @PathVariable Long brandId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "en") String language) {

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ProductResponseDTO> products = productService.getProductsByBrand(brandId, pageable, language);
        return ResponseEntity.ok(products);
    }

    @GetMapping(value = "/featured", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get featured products", description = "Retrieve featured products")
    public ResponseEntity<Page<ProductResponseDTO>> getFeaturedProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "en") String language) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<ProductResponseDTO> products = productService.getFeaturedProducts(pageable, language);
        return ResponseEntity.ok(products);
    }

    @GetMapping(value = "/on-sale", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get products on sale", description = "Retrieve products with discounts")
    public ResponseEntity<Page<ProductResponseDTO>> getProductsOnSale(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "en") String language) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("discount").descending());
        Page<ProductResponseDTO> products = productService.getProductsOnSale(pageable, language);
        return ResponseEntity.ok(products);
    }

    @Hidden
    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Search products", description = "Search products by text query")
    public ResponseEntity<Page<ProductResponseDTO>> searchProducts(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "en") String language) {

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ProductResponseDTO> products = productService.searchProducts(q, pageable, language);
        return ResponseEntity.ok(products);
    }

    @Hidden
    @GetMapping(value = "/filter", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Filter products", description = "Filter products with multiple criteria")
    public ResponseEntity<Page<ProductResponseDTO>> filterProducts(
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
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "en") String language) {

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        ProductStatus productStatus = status != null ? ProductStatus.valueOf(status) : null;

        Page<ProductResponseDTO> products = productService.filterProducts(
                categoryId, brandId, minPrice, maxPrice, productStatus, onSale, q, pageable, language);
        return ResponseEntity.ok(products);
    }

    @Hidden
    @PostMapping(value = "/filter/advanced", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Advanced product filtering", description = "Filter products with advanced specification-based filters")
    public ResponseEntity<Page<ProductResponseDTO>> filterProductsAdvanced(
            @RequestBody AdvancedFilterRequestDTO filterRequest,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "en") String language) {

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ProductResponseDTO> products = filteringService.filterProductsAdvanced(filterRequest, pageable, language);
        return ResponseEntity.ok(products);
    }

    @Hidden
    @GetMapping(value = "/{id}/related", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get related products", description = "Get products related to the specified product")
    public ResponseEntity<List<ProductResponseDTO>> getRelatedProducts(
            @PathVariable Long id,
            @RequestParam(defaultValue = "8") int limit,
            @RequestParam(defaultValue = "en") String language) {

        List<ProductResponseDTO> relatedProducts = productService.getRelatedProducts(id, limit, language);
        return ResponseEntity.ok(relatedProducts);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Create product", description = "Create a new product with required images in single operation")
    public ResponseEntity<ProductResponseDTO> createProduct(
            @RequestPart("product") @Valid ProductCreateRequestDTO productData,
            @RequestPart("primaryImage") MultipartFile primaryImage,
            @RequestPart(value = "additionalImages", required = false) List<MultipartFile> additionalImages,
            @RequestParam(defaultValue = "en") String language) {

        log.info("Creating product with reference number: {} and {} images",
                productData.getReferenceNumber(),
                1 + (additionalImages != null ? additionalImages.size() : 0));

        ProductResponseDTO createdProduct = productService.createProduct(
                productData, primaryImage, additionalImages, language);

        return ResponseEntity.status(HttpStatus.CREATED).body(createdProduct);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update product with image management", description = "Update product and manage images in single operation")
    public ResponseEntity<ProductResponseDTO> updateProduct(
            @PathVariable Long id,
            @RequestPart("product") @Valid ProductUpdateRequestDTO productData,
            @RequestPart(value = "newPrimaryImage", required = false) MultipartFile newPrimaryImage,
            @RequestPart(value = "newAdditionalImages", required = false) List<MultipartFile> newAdditionalImages,
            @RequestPart(value = "imageOperations", required = false) ProductImageOperationsDTO imageOperations,
            @RequestParam(defaultValue = "en") String language) {

        log.info("Updating product with id: {} with image operations", id);

        ProductResponseDTO updatedProduct = productService.updateProductWithImages(
                id, productData, newPrimaryImage, newAdditionalImages, imageOperations, language);

        return ResponseEntity.ok(updatedProduct);
    }

    @DeleteMapping("/{id}")
//    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Delete product", description = "Soft delete a product (Admin only)")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        log.info("Deleting product with id: {}", id);
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/permanent")
//    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Permanently delete product", description = "Permanently delete a product (Super Admin only)")
    public ResponseEntity<Void> permanentDeleteProduct(@PathVariable Long id) {
        log.info("Permanently deleting product with id: {}", id);
        productService.permanentDeleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @Hidden
    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Add image to existing product", description = "Add single image to existing product")
    public ResponseEntity<ProductImageUploadResponseDTO> addImageToProduct(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean isPrimary) {

        log.info("Adding image to existing product {} (isPrimary: {})", id, isPrimary);
        ProductImageUploadResponseDTO response = productService.addImageToProduct(id, file, isPrimary);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Hidden
    @DeleteMapping("/{id}/images")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Delete product image", description = "Delete specific image from product")
    public ResponseEntity<Void> deleteProductImage(
            @PathVariable Long id,
            @RequestParam String imageUrl) {

        log.info("Deleting image {} from product {}", imageUrl, id);
        productService.deleteProductImage(id, imageUrl);
        return ResponseEntity.noContent().build();
    }

    @Hidden
    @PutMapping(value = "/{id}/images/reorder", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Reorder product images", description = "Reorder existing product images")
    public ResponseEntity<ProductResponseDTO> reorderProductImages(
            @PathVariable Long id,
            @RequestBody @Valid List<ProductImageUpdateDTO> images,
            @RequestParam(defaultValue = "en") String language) {

        log.info("Reordering images for product {}", id);
        ProductResponseDTO updatedProduct = productService.reorderProductImages(id, images, language);
        return ResponseEntity.ok(updatedProduct);
    }
}