package com.techstore.repository;

import com.techstore.entity.Category;
import com.techstore.entity.Parameter;
import com.techstore.entity.ParameterOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParameterRepository extends JpaRepository<Parameter, Long> {

    Optional<Parameter> findByExternalId(Long externalId);

    List<Parameter> findAllByCategoryId(Long categoryId);

    boolean existsByNameBgIgnoreCaseAndCategory(String nameBg, Category category);
    boolean existsByNameEnIgnoreCaseAndCategory(String nameEn, Category category);

    List<Parameter> findByCategoryIdOrderByOrderAsc(Long categoryId);

    @Query("SELECT DISTINCT p FROM Parameter p " +
            "JOIN p.category c " +
            "WHERE c.id = :categoryId " +
            "AND EXISTS (SELECT prod FROM Product prod WHERE prod.category.id = c.id AND prod.status <> com.techstore.enums.ProductStatus.NOT_AVAILABLE) " +
            "ORDER BY p.order ASC")
    List<Parameter> findParametersForAvailableProductsByCategory(@Param("categoryId") Long categoryId);

    Optional<Parameter> findByExternalIdAndCategoryId(Long externalId, Long categoryId);

    Optional<Parameter> findByCategoryAndNameBg(Category category, String nameBg);

    List<Parameter> findByCategoryId(Long categoryId);

    long countByCategoryId(Long categoryId);

    Optional<Parameter> findByTekraKeyAndCategoryId(String tekraKey, Long categoryId);
}