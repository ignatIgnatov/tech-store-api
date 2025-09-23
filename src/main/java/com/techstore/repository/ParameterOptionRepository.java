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
public interface ParameterOptionRepository extends JpaRepository<ParameterOption, Long> {

    Optional<ParameterOption> findByExternalIdAndParameterId(Long externalId, Long parameterId);

    List<ParameterOption> findByParameterIdOrderByOrderAsc(Long parameterId);

    @Query("SELECT DISTINCT po FROM ParameterOption po " +
            "JOIN po.productParameters pp " +
            "JOIN pp.product p " +
            "WHERE po.parameter.id = :parameterId AND p.show = true AND p.status != 'NOT_AVAILABLE' " +
            "ORDER BY po.order ASC")
    List<ParameterOption> findOptionsForAvailableProductsByParameter(@Param("parameterId") Long parameterId);

    Optional<ParameterOption> findByParameterAndNameBg(Parameter parameter, String nameBg);
}