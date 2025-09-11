package com.techstore.repository;

import com.techstore.entity.CategorySpecificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategorySpecificationTemplateRepository extends JpaRepository<CategorySpecificationTemplate, Long> {

    List<CategorySpecificationTemplate> findByCategoryIdOrderBySortOrderAscSpecNameAsc(Long categoryId);

    List<CategorySpecificationTemplate> findByCategoryIdAndRequiredTrueOrderBySortOrderAsc(Long categoryId);

    List<CategorySpecificationTemplate> findByCategoryIdAndFilterableTrueOrderBySortOrderAsc(Long categoryId);

    @Query("SELECT DISTINCT t.specGroup FROM CategorySpecificationTemplate t WHERE t.category.id = :categoryId AND t.specGroup IS NOT NULL ORDER BY t.specGroup")
    List<String> findDistinctSpecGroupsByCategoryId(@Param("categoryId") Long categoryId);

    boolean existsByCategoryIdAndSpecName(Long categoryId, String specName);

    boolean existsByCategoryIdAndSpecNameAndIdNot(Long categoryId, String specName, Long id);
}
