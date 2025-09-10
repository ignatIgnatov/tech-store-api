package com.techstore.repository;

import com.techstore.entity.Brand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BrandRepository extends JpaRepository<Brand, Long> {

    // Find by slug
    Optional<Brand> findBySlug(String slug);

    // Check if slug exists
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, Long id);

    // Find active brands
    List<Brand> findByActiveTrueOrderBySortOrderAscNameAsc();
    Page<Brand> findByActiveTrue(Pageable pageable);

    // Find featured brands
    List<Brand> findByActiveTrueAndFeaturedTrueOrderBySortOrderAscNameAsc();

    // Find brands with products
    @Query("SELECT DISTINCT b FROM Brand b WHERE b.active = true AND " +
            "EXISTS (SELECT p FROM Product p WHERE p.brand = b AND p.active = true)")
    List<Brand> findBrandsWithProducts();

    // Search brands
    @Query("SELECT b FROM Brand b WHERE b.active = true AND " +
            "LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Brand> searchBrands(@Param("query") String query);

    // Find brands by country
    List<Brand> findByActiveTrueAndCountryOrderByNameAsc(String country);

    // Get brand statistics
    @Query("SELECT b.id, b.name, COUNT(p) as productCount FROM Brand b " +
            "LEFT JOIN Product p ON p.brand = b AND p.active = true " +
            "WHERE b.active = true GROUP BY b.id, b.name ORDER BY productCount DESC")
    List<Object[]> getBrandStatistics();
}
