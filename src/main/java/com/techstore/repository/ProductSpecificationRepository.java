

package com.techstore.repository;

import com.techstore.dto.NumericRange;
import com.techstore.entity.CategorySpecificationTemplate;
import com.techstore.entity.Product;
import com.techstore.entity.ProductSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ProductSpecificationRepository extends JpaRepository<ProductSpecification, Long> {

    // ===== EXISTING METHODS =====

    List<ProductSpecification> findByProductIdOrderByTemplateSpecGroupAscTemplateSortOrderAsc(Long productId);

    List<ProductSpecification> findByProductIdAndTemplateSpecGroup(Long productId, String specGroup);

    @Query("SELECT DISTINCT ps.specValue FROM ProductSpecification ps WHERE ps.template.id = :templateId ORDER BY ps.specValue")
    List<String> findDistinctValuesByTemplateId(@Param("templateId") Long templateId);

    @Query(value = "SELECT CAST(MIN(ps.spec_value) AS DECIMAL) AS minValue, " +
            "CAST(MAX(ps.spec_value) AS DECIMAL) AS maxValue " +
            "FROM product_specifications ps " +
            "WHERE ps.template_id = :templateId",
            nativeQuery = true)
    NumericRange findNumericRangeByTemplateId(@Param("templateId") Long templateId);

    // For advanced filtering
    @Query("SELECT p FROM Product p JOIN p.specifications ps WHERE ps.template.id = :templateId AND ps.specValue = :value AND p.active = true")
    List<Product> findProductsBySpecification(@Param("templateId") Long templateId, @Param("value") String value);

    @Query(value = "SELECT p.* FROM products p " +
            "JOIN product_specifications ps ON p.id = ps.product_id " +
            "WHERE ps.template_id = :templateId " +
            "AND CAST(ps.spec_value AS DECIMAL) BETWEEN :minValue AND :maxValue " +
            "AND p.active = true",
            nativeQuery = true)
    List<Product> findProductsByNumericRange(@Param("templateId") Long templateId,
                                             @Param("minValue") BigDecimal minValue,
                                             @Param("maxValue") BigDecimal maxValue);

    // ===== MISSING SEARCH METHODS =====

    /**
     * Search specifications by query in spec names and values
     * Used by EnhancedSearchService.searchWithSpecifications()
     */
    @Query("SELECT ps FROM ProductSpecification ps " +
            "WHERE (LOWER(ps.template.specName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(ps.specValue) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "AND ps.product.active = true")
    List<ProductSpecification> searchSpecifications(@Param("query") String query);

    /**
     * Search specifications with pagination
     */
    @Query("SELECT ps FROM ProductSpecification ps " +
            "WHERE (LOWER(ps.template.specName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(ps.specValue) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "AND ps.product.active = true " +
            "ORDER BY ps.template.specName, ps.specValue")
    Page<ProductSpecification> searchSpecifications(@Param("query") String query, Pageable pageable);

    /**
     * Search specifications within a specific category
     */
    @Query("SELECT ps FROM ProductSpecification ps " +
            "WHERE ps.product.category.id = :categoryId " +
            "AND (LOWER(ps.template.specName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(ps.specValue) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "AND ps.product.active = true")
    List<ProductSpecification> searchSpecificationsByCategory(@Param("categoryId") Long categoryId,
                                                              @Param("query") String query);

    /**
     * Search for products by specification value across all specs
     */
    @Query("SELECT DISTINCT ps.product FROM ProductSpecification ps " +
            "WHERE LOWER(ps.specValue) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "AND ps.product.active = true")
    List<Product> findProductsBySpecificationValue(@Param("query") String query);

    /**
     * Find specifications that contain specific keywords
     */
    @Query("SELECT ps FROM ProductSpecification ps " +
            "WHERE ps.template.searchable = true " +
            "AND (LOWER(ps.template.specName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(ps.specValue) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND ps.product.active = true")
    List<ProductSpecification> findSpecificationsByKeyword(@Param("keyword") String keyword);

    /**
     * Advanced search with multiple criteria
     */
    @Query("SELECT ps FROM ProductSpecification ps " +
            "WHERE (:categoryId IS NULL OR ps.product.category.id = :categoryId) " +
            "AND (:brandId IS NULL OR ps.product.brand.id = :brandId) " +
            "AND (:templateId IS NULL OR ps.template.id = :templateId) " +
            "AND (:query IS NULL OR " +
            "    LOWER(ps.template.specName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "    LOWER(ps.specValue) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "AND ps.product.active = true " +
            "ORDER BY ps.template.sortOrder, ps.template.specName")
    List<ProductSpecification> searchSpecificationsAdvanced(@Param("categoryId") Long categoryId,
                                                            @Param("brandId") Long brandId,
                                                            @Param("templateId") Long templateId,
                                                            @Param("query") String query);

    /**
     * Get specification statistics for search results
     */
    @Query("SELECT ps.template.specName as specName, COUNT(ps) as count " +
            "FROM ProductSpecification ps " +
            "WHERE LOWER(ps.specValue) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "AND ps.product.active = true " +
            "GROUP BY ps.template.specName " +
            "ORDER BY count DESC")
    List<Object[]> getSpecificationSearchStatistics(@Param("query") String query);

    /**
     * Find similar specifications (for suggestions)
     */
    @Query("SELECT DISTINCT ps.specValue FROM ProductSpecification ps " +
            "WHERE ps.template.id = :templateId " +
            "AND LOWER(ps.specValue) LIKE LOWER(CONCAT('%', :partialValue, '%')) " +
            "AND ps.product.active = true " +
            "ORDER BY ps.specValue " +
            "LIMIT 10")
    List<String> findSimilarSpecificationValues(@Param("templateId") Long templateId,
                                                @Param("partialValue") String partialValue);

    /**
     * Search for exact specification matches (for autocomplete)
     */
    @Query("SELECT DISTINCT ps.specValue FROM ProductSpecification ps " +
            "WHERE ps.template.id = :templateId " +
            "AND LOWER(ps.specValue) LIKE LOWER(CONCAT(:value, '%')) " +
            "AND ps.product.active = true " +
            "ORDER BY ps.specValue " +
            "LIMIT 5")
    List<String> findSpecificationValueSuggestions(@Param("templateId") Long templateId,
                                                   @Param("value") String value);

    // ===== ADDITIONAL UTILITY METHODS =====

    /**
     * Find all distinct specification values for a template in a category
     */
    @Query("SELECT DISTINCT ps.specValue FROM ProductSpecification ps " +
            "WHERE ps.template.id = :templateId " +
            "AND ps.product.category.id = :categoryId " +
            "AND ps.product.active = true " +
            "ORDER BY ps.specValue")
    List<String> findDistinctValuesByTemplateAndCategory(@Param("templateId") Long templateId,
                                                         @Param("categoryId") Long categoryId);

    /**
     * Count specifications by template and value (for filter counts)
     */
    @Query("SELECT ps.specValue, COUNT(ps.product) FROM ProductSpecification ps " +
            "WHERE ps.template.id = :templateId " +
            "AND ps.product.active = true " +
            "GROUP BY ps.specValue " +
            "ORDER BY COUNT(ps.product) DESC")
    List<Object[]> countProductsBySpecificationValue(@Param("templateId") Long templateId);

    /**
     * Delete specifications by product ID
     */
    void deleteByProductId(Long productId);

    /**
     * Count specifications by product
     */
    @Query("SELECT COUNT(ps) FROM ProductSpecification ps WHERE ps.product.id = :productId")
    Long countByProductId(@Param("productId") Long productId);

    /**
     * Find missing required specifications for a product
     */
    @Query("SELECT t FROM CategorySpecificationTemplate t " +
            "WHERE t.category.id = :categoryId " +
            "AND t.required = true " +
            "AND t.id NOT IN (" +
            "    SELECT ps.template.id FROM ProductSpecification ps " +
            "    WHERE ps.product.id = :productId" +
            ")")
    List<CategorySpecificationTemplate> findMissingRequiredSpecs(@Param("productId") Long productId,
                                                                 @Param("categoryId") Long categoryId);
}
