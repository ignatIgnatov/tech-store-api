package com.techstore.repository;

import com.techstore.entity.Product;
import com.techstore.enums.ProductStatus;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Find products by both category and manufacturer
     */
    Page<Product> findByActiveTrueAndCategoryIdAndManufacturerId(
            Long categoryId, Long manufacturerId, Pageable pageable);

    /**
     * Find products by category with optional manufacturer filter
     */
    @Query("SELECT p FROM Product p WHERE p.active = true " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (:manufacturerId IS NULL OR p.manufacturer.id = :manufacturerId)")
    Page<Product> findByActiveTrueWithFilters(
            @Param("categoryId") Long categoryId,
            @Param("manufacturerId") Long manufacturerId,
            Pageable pageable);

    /**
     * Find products by multiple criteria with null safety
     */
    @Query("SELECT p FROM Product p WHERE p.active = true " +
            "AND p.show = true " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (:manufacturerId IS NULL OR p.manufacturer.id = :manufacturerId) " +
            "AND (:minPrice IS NULL OR p.finalPrice >= :minPrice) " +
            "AND (:maxPrice IS NULL OR p.finalPrice <= :maxPrice) " +
            "AND (:featured IS NULL OR p.featured = :featured)")
    Page<Product> findProductsWithMultipleFilters(
            @Param("categoryId") Long categoryId,
            @Param("manufacturerId") Long manufacturerId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("featured") Boolean featured,
            Pageable pageable);

    boolean existsByReferenceNumberIgnoreCase(String referenceNumber);

    Optional<Product> findByExternalId(Long externalId);

    Page<Product> findByActiveTrue(Pageable pageable);

    List<Product> findByActiveTrueOrderByNameEnAsc();

    Page<Product> findByActiveTrueAndFeaturedTrue(Pageable pageable);

    Page<Product> findByActiveTrueAndCategoryId(Long categoryId, Pageable pageable);

    Page<Product> findByActiveTrueAndManufacturerId(Long brandId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND p.discount IS NOT NULL AND p.discount != 0")
    Page<Product> findProductsOnSale(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND " +
            "(" +
            "LOWER(p.nameEn) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(p.nameBg) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(p.descriptionBg) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(p.descriptionEn) LIKE LOWER(CONCAT('%', :query, '%'))" +
            ")")
    Page<Product> searchProducts(@Param("query") String query, Pageable pageable);


    @Query("SELECT p FROM Product p WHERE p.active = true AND " +
            "p.priceClientPromo >= :minPrice AND p.priceClientPromo <= :maxPrice")
    Page<Product> findByPriceRange(@Param("minPrice") BigDecimal minPrice,
                                   @Param("maxPrice") BigDecimal maxPrice,
                                   Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true " +
            "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
            "AND (:manufacturerId IS NULL OR p.manufacturer.id = :manufacturerId) " +
            "AND (:minPrice IS NULL OR p.priceClientPromo >= :minPrice) " +
            "AND (:maxPrice IS NULL OR p.priceClientPromo <= :maxPrice) " +
            "AND (:status IS NULL OR p.status = :status) " +
            "AND (:onSale IS NULL OR (:onSale = true AND p.discount IS NOT NULL AND p.discount <> 0) OR (:onSale = false)) " +
            "AND (:query IS NULL OR LOWER(p.nameEn) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "    OR LOWER(p.descriptionEn) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "    OR LOWER(p.referenceNumber) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "    OR LOWER(p.model) LIKE LOWER(CONCAT('%', :query, '%'))) ")
    Page<Product> findProductsWithFilters(@Param("categoryId") Long categoryId,
                                          @Param("manufacturerId") Long manufacturerId,
                                          @Param("minPrice") BigDecimal minPrice,
                                          @Param("maxPrice") BigDecimal maxPrice,
                                          @Param("status") ProductStatus status,
                                          @Param("onSale") Boolean onSale,
                                          @Param("query") String query,
                                          Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND p.id != :productId AND " +
            "(p.category.id = :categoryId OR p.manufacturer.id = :manufacturerId)")
    List<Product> findRelatedProducts(@Param("productId") Long productId,
                                      @Param("categoryId") Long categoryId,
                                      @Param("manufacturerId") Long manufacturerId,
                                      Pageable pageable);

    @Query("SELECT MIN(p.finalPrice) FROM Product p WHERE p.show = true AND p.status != 'NOT_AVAILABLE' AND p.finalPrice IS NOT NULL")
    Optional<BigDecimal> findMinPrice();

    @Query("SELECT MAX(p.finalPrice) FROM Product p WHERE p.show = true AND p.status != 'NOT_AVAILABLE' AND p.finalPrice IS NOT NULL")
    Optional<BigDecimal> findMaxPrice();

    @Query("SELECT COUNT(p) FROM Product p WHERE p.show = true AND p.status != 'NOT_AVAILABLE'")
    Long countAvailableProducts();

    List<Product> findAllByCategoryId(Long categoryId);

    Optional<Product> findByTekraId(String tekraId);
    List<Product> findByTekraIdIsNotNull();

    @Query("SELECT p FROM Product p WHERE p.tekraId IS NOT NULL AND p.updatedAt < :date")
    List<Product> findTekraProductsOlderThan(@Param("date") LocalDateTime date);

    Optional<Product> findBySku(String sku);
}