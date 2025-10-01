package com.techstore.repository;

import com.techstore.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByExternalId(Long externalId);

    Optional<Category> findBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    List<Category> findByShowTrueOrderBySortOrderAscNameEnAsc();

    Page<Category> findByShowTrue(Pageable pageable);

    Optional<Category> findByTekraSlug(String tekraSlug);

    Optional<Category> findByNameBg(String nameBg);

    Optional<Category> findByNameEn(String nameEn);

    List<Category> findByParentId(Long parentId);
}