package com.techstore.repository;

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

    List<Parameter> findByCategoryIdOrderByOrderAsc(Long categoryId);

    @Query("SELECT DISTINCT p FROM Parameter p " +
            "JOIN p.category c " +
            "JOIN c.products prod " +
            "WHERE c.id = :categoryId " +
            "AND prod.show = true " +
            "AND prod.status <> com.techstore.enums.ProductStatus.NOT_AVAILABLE " +
            "ORDER BY p.order ASC")
    List<Parameter> findParametersForAvailableProductsByCategory(@Param("categoryId") Long categoryId);


    Optional<Parameter> findByExternalIdAndCategoryId(Long externalId, Long categoryId);

    @Query("SELECT po FROM ParameterOption po WHERE po.externalId = :externalId AND po.parameter.id = :parameterId")
    Optional<ParameterOption> findByExternalIdAndParameterId(@Param("externalId") Long externalId,
                                                             @Param("parameterId") Long parameterId);
}