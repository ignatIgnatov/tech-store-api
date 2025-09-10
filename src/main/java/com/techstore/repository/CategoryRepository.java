package com.techstore.repository;

import com.techstore.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    // Find by slug
    Optional<Category> findBySlug(String slug);

    // Check if slug exists
    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    // Find active categories
    List<Category> findByActiveTrueOrderBySortOrderAscNameAsc();

    Page<Category> findByActiveTrue(Pageable pageable);

    // Find parent categories (top-level)
    List<Category> findByActiveTrueAndParentIsNullOrderBySortOrderAscNameAsc();

    // Find child categories
    List<Category> findByActiveTrueAndParentIdOrderBySortOrderAscNameAsc(Long parentId);

    // Find categories with products
    @Query("SELECT DISTINCT c FROM Category c WHERE c.active = true AND " +
            "EXISTS (SELECT p FROM Product p WHERE p.category = c AND p.active = true)")
    List<Category> findCategoriesWithProducts();

    // Search categories
    @Query("SELECT c FROM Category c WHERE c.active = true AND " +
            "LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Category> searchCategories(@Param("query") String query);

    // Get category hierarchy
    @Query("SELECT c FROM Category c WHERE c.active = true ORDER BY " +
            "CASE WHEN c.parent IS NULL THEN c.sortOrder ELSE c.parent.sortOrder END, " +
            "c.parent.id, c.sortOrder, c.name")
    List<Category> findCategoryHierarchy();
}
