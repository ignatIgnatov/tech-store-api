package com.techstore.repository;

import com.techstore.entity.Product;
import com.techstore.enums.ProductStatus;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p FROM Product p WHERE p.externalId = :externalId")
    @QueryHints(@QueryHint(name = org.hibernate.annotations.QueryHints.FLUSH_MODE, value = "ALWAYS"))
    Optional<Product> findByExternalIdWithSessionClear(@Param("externalId") Long externalId);

    Optional<Product> findByExternalId(Long externalId);

    // Find by SKU
    Optional<Product> findBySku(String sku);

    // Check if SKU exists (for validation)
    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);

    // Find active products
    Page<Product> findByActiveTrue(Pageable pageable);

    List<Product> findByActiveTrueOrderByNameEnAsc();

    // Find featured products
    Page<Product> findByActiveTrueAndFeaturedTrue(Pageable pageable);

    List<Product> findByActiveTrueAndFeaturedTrueOrderByCreatedAtDesc();

    // Find by category
    Page<Product> findByActiveTrueAndCategoryId(Long categoryId, Pageable pageable);

    Page<Product> findByActiveTrueAndCategoryIdIn(List<Long> categoryIds, Pageable pageable);

    // Find by brand
    Page<Product> findByActiveTrueAndBrandId(Long brandId, Pageable pageable);

    // Find products in stock
    Page<Product> findByActiveTrueAndStockQuantityGreaterThan(Integer quantity, Pageable pageable);

    // Find products on sale (with discount)
    @Query("SELECT p FROM Product p WHERE p.active = true AND p.discount IS NOT NULL AND p.discount != 0")
    Page<Product> findProductsOnSale(Pageable pageable);

    // Search products by name or description
    @Query("SELECT p FROM Product p WHERE p.active = true AND " +
            "(LOWER(p.nameEn) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Product> searchProducts(@Param("query") String query, Pageable pageable);

    // Find products by price range
    @Query("SELECT p FROM Product p WHERE p.active = true AND " +
            "p.discountedPrice >= :minPrice AND p.discountedPrice <= :maxPrice")
    Page<Product> findByPriceRange(@Param("minPrice") BigDecimal minPrice,
                                   @Param("maxPrice") BigDecimal maxPrice,
                                   Pageable pageable);

    // Complex filter query
    @Query("SELECT p FROM Product p WHERE p.active = true " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (:brandId IS NULL OR p.brand.id = :brandId) " +
            "AND (:minPrice IS NULL OR p.discountedPrice >= :minPrice) " +
            "AND (:maxPrice IS NULL OR p.discountedPrice <= :maxPrice) " +
            "AND (:inStock IS NULL OR (:inStock = true AND p.stockQuantity > 0) OR (:inStock = false)) " +
            "AND (:onSale IS NULL OR (:onSale = true AND p.discount IS NOT NULL AND p.discount != 0) OR (:onSale = false)) " +
            "AND (:query IS NULL OR LOWER(p.nameEn) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "    OR LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "    OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Product> findProductsWithFilters(@Param("categoryId") Long categoryId,
                                          @Param("brandId") Long brandId,
                                          @Param("minPrice") BigDecimal minPrice,
                                          @Param("maxPrice") BigDecimal maxPrice,
                                          @Param("inStock") Boolean inStock,
                                          @Param("onSale") Boolean onSale,
                                          @Param("query") String query,
                                          Pageable pageable);

    // Get latest products
    @Query("SELECT p FROM Product p WHERE p.active = true ORDER BY p.createdAt DESC")
    Page<Product> findLatestProducts(Pageable pageable);

    // Get popular products (you can customize this logic)
    @Query("SELECT p FROM Product p WHERE p.active = true AND p.featured = true ORDER BY p.createdAt DESC")
    Page<Product> findPopularProducts(Pageable pageable);

    // Find related products (same category, different brand or vice versa)
    @Query("SELECT p FROM Product p WHERE p.active = true AND p.id != :productId AND " +
            "(p.category.id = :categoryId OR p.brand.id = :brandId)")
    List<Product> findRelatedProducts(@Param("productId") Long productId,
                                      @Param("categoryId") Long categoryId,
                                      @Param("brandId") Long brandId,
                                      Pageable pageable);

    // Count products by category
    @Query("SELECT COUNT(p) FROM Product p WHERE p.active = true AND p.category.id = :categoryId")
    Long countByCategory(@Param("categoryId") Long categoryId);

    // Count products by brand
    @Query("SELECT COUNT(p) FROM Product p WHERE p.active = true AND p.brand.id = :brandId")
    Long countByBrand(@Param("brandId") Long brandId);

    // Get price range for a category
    @Query("SELECT MIN(p.discountedPrice), MAX(p.discountedPrice) FROM Product p " +
            "WHERE p.active = true AND p.category.id = :categoryId")
    Object[] getPriceRangeByCategory(@Param("categoryId") Long categoryId);

    // Get price range for a brand
    @Query("SELECT MIN(p.discountedPrice), MAX(p.discountedPrice) FROM Product p " +
            "WHERE p.active = true AND p.brand.id = :brandId")
    Object[] getPriceRangeByBrand(@Param("brandId") Long brandId);

    // Find products with low stock
    @Query("SELECT p FROM Product p WHERE p.active = true AND p.stockQuantity <= :threshold")
    List<Product> findLowStockProducts(@Param("threshold") Integer threshold);

    // Get products that need restock
    @Query("SELECT p FROM Product p WHERE p.active = true AND p.stockQuantity = 0")
    List<Product> findOutOfStockProducts();

    @Query("SELECT MIN(p.finalPrice) FROM Product p WHERE p.show = true AND p.status != 'NOT_AVAILABLE' AND p.finalPrice IS NOT NULL")
    Optional<BigDecimal> findMinPrice();

    @Query("SELECT MAX(p.finalPrice) FROM Product p WHERE p.show = true AND p.status != 'NOT_AVAILABLE' AND p.finalPrice IS NOT NULL")
    Optional<BigDecimal> findMaxPrice();

    List<Product> findByStatusAndShowTrue(ProductStatus status);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.show = true AND p.status != 'NOT_AVAILABLE'")
    Long countAvailableProducts();
}