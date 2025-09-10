package com.techstore.repository;

import com.techstore.entity.ProductSpecification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductSpecificationRepository extends JpaRepository<ProductSpecification, Long> {

    // Find by product
    List<ProductSpecification> findByProductIdOrderBySortOrderAscSpecNameAsc(Long productId);

    // Find by product and group
    List<ProductSpecification> findByProductIdAndSpecGroupOrderBySortOrderAscSpecNameAsc(Long productId, String specGroup);

    // Find distinct spec groups for a product
    @Query("SELECT DISTINCT ps.specGroup FROM ProductSpecification ps WHERE ps.product.id = :productId AND ps.specGroup IS NOT NULL ORDER BY ps.specGroup")
    List<String> findDistinctSpecGroupsByProductId(@Param("productId") Long productId);

    // Find distinct spec names for a category
    @Query("SELECT DISTINCT ps.specName FROM ProductSpecification ps " +
            "WHERE ps.product.category.id = :categoryId ORDER BY ps.specName")
    List<String> findDistinctSpecNamesByCategoryId(@Param("categoryId") Long categoryId);

    // Find distinct spec names for a brand
    @Query("SELECT DISTINCT ps.specName FROM ProductSpecification ps " +
            "WHERE ps.product.brand.id = :brandId ORDER BY ps.specName")
    List<String> findDistinctSpecNamesByBrandId(@Param("brandId") Long brandId);

    // Search specifications
    @Query("SELECT ps FROM ProductSpecification ps WHERE " +
            "(LOWER(ps.specName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(ps.specValue) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<ProductSpecification> searchSpecifications(@Param("query") String query);

    // Delete by product
    void deleteByProductId(Long productId);

    // Count specifications by product
    @Query("SELECT COUNT(ps) FROM ProductSpecification ps WHERE ps.product.id = :productId")
    Long countByProductId(@Param("productId") Long productId);
}
