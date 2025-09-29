package com.techstore.repository;

import com.techstore.entity.Parameter;
import com.techstore.entity.ParameterOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParameterOptionRepository extends JpaRepository<ParameterOption, Long> {

    Optional<ParameterOption> findByExternalIdAndParameterId(Long externalId, Long parameterId);

    List<ParameterOption> findByParameterIdOrderByOrderAsc(Long parameterId);

    Optional<ParameterOption> findByParameterAndNameBg(Parameter parameter, String nameBg);
}