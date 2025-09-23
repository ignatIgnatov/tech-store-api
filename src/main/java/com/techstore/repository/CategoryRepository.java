package com.techstore.repository;

import com.techstore.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByExternalId(Long externalId);

    @Query("SELECT DISTINCT c FROM Category c " +
            "JOIN c.products p " +
            "WHERE c.show = true " +
            "AND p.show = true AND p.status <> com.techstore.enums.ProductStatus.NOT_AVAILABLE")
    List<Category> findCategoriesWithAvailableProducts();

    Optional<Category> findBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    List<Category> findByShowTrueOrderBySortOrderAscNameEnAsc();

    Page<Category> findByShowTrue(Pageable pageable);

    Optional<Category> findByTekraId(String tekraId);

    Optional<Category> findByTekraSlug(String tekraSlug);

    List<Category> findByTekraIdIsNotNull();

    Optional<Category> findByNameBg(String nameBg);

    Optional<Category> findByNameEn(String nameEn);
}
