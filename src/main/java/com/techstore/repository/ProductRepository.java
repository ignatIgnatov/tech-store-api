package com.techstore.repository;

import com.techstore.entity.Product;
import com.techstore.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    boolean existsByReferenceNumberIgnoreCase(String referenceNumber);

    Optional<Product> findByExternalId(Long externalId);

    Page<Product> findByActiveTrue(Pageable pageable);

    List<Product> findByActiveTrueOrderByNameEnAsc();

    Page<Product> findByActiveTrueAndFeaturedTrue(Pageable pageable);

    Page<Product> findByActiveTrueAndCategoryId(Long categoryId, Pageable pageable);

    Page<Product> findByActiveTrueAndManufacturerId(Long brandId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND p.show = true AND p.discount IS NOT NULL AND p.discount != 0")
    Page<Product> findProductsOnSale(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND p.show = true AND " +
            "(" +
            "LOWER(p.nameEn) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(p.nameBg) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(p.descriptionBg) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(p.descriptionEn) LIKE LOWER(CONCAT('%', :query, '%'))" +
            ")")
    Page<Product> searchProducts(@Param("query") String query, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND p.show = true AND " +
            "p.priceClientPromo >= :minPrice AND p.priceClientPromo <= :maxPrice")
    Page<Product> findByPriceRange(@Param("minPrice") BigDecimal minPrice,
                                   @Param("maxPrice") BigDecimal maxPrice,
                                   Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true " +
            "AND p.show = true " +
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

    @Query("SELECT p FROM Product p WHERE p.active = true AND p.show = true AND p.id != :productId AND " +
            "(p.category.id = :categoryId OR p.manufacturer.id = :manufacturerId)")
    List<Product> findRelatedProducts(@Param("productId") Long productId,
                                      @Param("categoryId") Long categoryId,
                                      @Param("manufacturerId") Long manufacturerId,
                                      Pageable pageable);

    List<Product> findAllByCategoryId(Long categoryId);

    Optional<Product> findBySku(String sku);

    long countByCategoryId(Long categoryId);

    List<Product> findByCategoryId(Long categoryId);

    @Query("SELECT p.sku, COUNT(p) FROM Product p WHERE p.sku IS NOT NULL GROUP BY p.sku HAVING COUNT(p) > 1")
    List<Object[]> findDuplicateProductsBySku();

    @Query("SELECT p.externalId, COUNT(p) FROM Product p WHERE p.externalId IS NOT NULL GROUP BY p.externalId HAVING COUNT(p) > 1")
    List<Object[]> findDuplicateProductsByExternalId();

    ;

    @Query("SELECT p FROM Product p WHERE p.externalId = :externalId")
    List<Product> findProductsByExternalId(@Param("externalId") Long externalId);

    @Query("SELECT p FROM Product p WHERE p.sku = :sku")
    List<Product> findProductsBySkuCode(@Param("sku") String sku);

    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.category.id = :newCategoryId WHERE p.category.id = :oldCategoryId")
    int updateCategoryForProducts(@Param("oldCategoryId") Long oldCategoryId,
                                  @Param("newCategoryId") Long newCategoryId);
}
