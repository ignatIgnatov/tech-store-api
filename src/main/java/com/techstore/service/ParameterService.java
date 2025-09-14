package com.techstore.service;

import com.techstore.dto.external.ExternalParameterDto;
import com.techstore.dto.response.ParameterResponseDto;
import com.techstore.entity.Category;
import com.techstore.entity.Parameter;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.mapper.ParameterMapper;
import com.techstore.repository.ParameterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParameterService {

    private final ParameterRepository parameterRepository;
    private final ParameterMapper parameterMapper;

    @Cacheable(value = "parameters", key = "'category_' + #categoryId + '_' + #language")
    public List<ParameterResponseDto> getParametersByCategory(Long categoryId, String language) {
        List<Parameter> parameters = parameterRepository.findParametersForAvailableProductsByCategory(categoryId);
        return parameters.stream()
                .map(parameter -> parameterMapper.toResponseDto(parameter, language))
                .toList();
    }

    @Cacheable(value = "parameters", key = "#id + '_' + #language")
    public ParameterResponseDto getParameterById(Long id, String language) {
        Parameter parameter = parameterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Parameter not found with id: " + id));

        return parameterMapper.toResponseDto(parameter, language);
    }

    private Parameter createParameterFromExternal(ExternalParameterDto extParameter, Category category) {
        Parameter parameter = new Parameter();
        parameter.setExternalId(extParameter.getId());
        parameter.setCategory(category);
        parameter.setOrder(extParameter.getOrder());

        if (extParameter.getName() != null) {
            extParameter.getName().forEach(name -> {
                if ("bg".equals(name.getLanguageCode())) {
                    parameter.setNameBg(name.getText());
                } else if ("en".equals(name.getLanguageCode())) {
                    parameter.setNameEn(name.getText());
                }
            });
        }

        return parameter;
    }

    private void updateParameterFromExternal(Parameter parameter, ExternalParameterDto extParameter) {
        parameter.setOrder(extParameter.getOrder());

        if (extParameter.getName() != null) {
            extParameter.getName().forEach(name -> {
                if ("bg".equals(name.getLanguageCode())) {
                    parameter.setNameBg(name.getText());
                } else if ("en".equals(name.getLanguageCode())) {
                    parameter.setNameEn(name.getText());
                }
            });
        }
    }
}