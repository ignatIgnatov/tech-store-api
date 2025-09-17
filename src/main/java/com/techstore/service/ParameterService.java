package com.techstore.service;

import com.techstore.dto.request.ParameterOptionRequestDto;
import com.techstore.dto.request.ParameterRequestDto;
import com.techstore.dto.response.ParameterResponseDto;
import com.techstore.entity.Category;
import com.techstore.entity.Parameter;
import com.techstore.entity.ParameterOption;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.mapper.ParameterMapper;
import com.techstore.repository.CategoryRepository;
import com.techstore.repository.ParameterOptionRepository;
import com.techstore.repository.ParameterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParameterService {

    private final ParameterRepository parameterRepository;
    private final ParameterMapper parameterMapper;
    private final ParameterOptionRepository parameterOptionRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public ParameterResponseDto createParameter(ParameterRequestDto requestDto, String language) {

        Category category = categoryRepository.findById(requestDto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id " + requestDto.getCategoryId()));

        Parameter parameter = new Parameter();
        parameter.setCategory(category);
        if (requestDto.getName() != null) {
            requestDto.getName().forEach(name -> {
                if ("bg".equals(name.getLanguageCode())) {
                    parameter.setNameBg(name.getText());
                } else if ("en".equals(name.getLanguageCode())) {
                    parameter.setNameEn(name.getText());
                }
            });
        }
        parameter.setOrder(requestDto.getOrder());

        Map<Long, ParameterOption> existingOptions = parameterOptionRepository
                .findByParameterIdOrderByOrderAsc(parameter.getId())
                .stream()
                .collect(Collectors.toMap(ParameterOption::getExternalId, o -> o));

        for (ParameterOptionRequestDto extOption : requestDto.getOptions()) {
            ParameterOption option = existingOptions.get(extOption.getId());

            if (option == null) {
                option = createParameterOptionFromExternal(extOption, parameter);
                parameterOptionRepository.save(option);
            } else {
                updateParameterOptionFromExternal(option, extOption);
                parameterOptionRepository.save(option);
            }
        }

        parameterRepository.save(parameter);

        return parameterMapper.toResponseDto(parameter, language);
    }

    @Transactional
    public ParameterResponseDto updateParameter(Long id, ParameterRequestDto requestDto, String language) {

        Parameter parameter = parameterRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Parameter not found with id " + id));

        if (requestDto.getName() != null) {
            requestDto.getName().forEach(name -> {
                if ("bg".equals(name.getLanguageCode())) {
                    parameter.setNameBg(name.getText());
                } else if ("en".equals(name.getLanguageCode())) {
                    parameter.setNameEn(name.getText());
                }
            });
        }
        parameter.setOrder(requestDto.getOrder());

        Map<Long, ParameterOption> existingOptions = parameterOptionRepository
                .findByParameterIdOrderByOrderAsc(parameter.getId())
                .stream()
                .collect(Collectors.toMap(ParameterOption::getExternalId, o -> o));

        for (ParameterOptionRequestDto extOption : requestDto.getOptions()) {
            ParameterOption option = existingOptions.get(extOption.getId());

            if (option == null) {
                option = createParameterOptionFromExternal(extOption, parameter);
                parameterOptionRepository.save(option);
            } else {
                updateParameterOptionFromExternal(option, extOption);
                parameterOptionRepository.save(option);
            }
        }

        parameterRepository.save(parameter);

        return parameterMapper.toResponseDto(parameter, language);
    }

    @Transactional
    public void deleteParameter(Long parameterId) {
        Parameter parameter = parameterRepository.findById(parameterId)
                .orElseThrow(() -> new ResourceNotFoundException("Parameter not found with id " + parameterId));
        parameterRepository.delete(parameter);
    }

    @Cacheable(value = "parameters", key = "'category_' + #categoryId + '_' + #language")
    public List<ParameterResponseDto> getParametersByCategory(Long categoryId, String language) {
        List<Parameter> parameters = parameterRepository.findParametersForAvailableProductsByCategory(categoryId);
        return parameters.stream()
                .map(parameter -> parameterMapper.toResponseDto(parameter, language))
                .toList();
    }

    @Cacheable(value = "parameters", key = "'category_' + #categoryId + '_' + #language")
    public List<ParameterResponseDto> findByCategory(Long categoryId, String language) {
        List<Parameter> parameters = parameterRepository.findByCategoryIdOrderByOrderAsc(categoryId);
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

    public List<ParameterResponseDto> getAllParameters(String lang) {
        return parameterRepository.findAll().stream()
                .map(p -> parameterMapper.toResponseDto(p, lang))
                .toList();
    }

    private ParameterOption createParameterOptionFromExternal(ParameterOptionRequestDto extOption, Parameter parameter) {
        ParameterOption option = new ParameterOption();
        option.setExternalId(extOption.getId());
        option.setParameter(parameter);
        option.setOrder(extOption.getOrder());

        if (extOption.getName() != null) {
            extOption.getName().forEach(name -> {
                if ("bg".equals(name.getLanguageCode())) {
                    option.setNameBg(name.getText());
                } else if ("en".equals(name.getLanguageCode())) {
                    option.setNameEn(name.getText());
                }
            });
        }

        return option;
    }

    private void updateParameterOptionFromExternal(ParameterOption option, ParameterOptionRequestDto extOption) {
        option.setOrder(extOption.getOrder());

        if (extOption.getName() != null) {
            extOption.getName().forEach(name -> {
                if ("bg".equals(name.getLanguageCode())) {
                    option.setNameBg(name.getText());
                } else if ("en".equals(name.getLanguageCode())) {
                    option.setNameEn(name.getText());
                }
            });
        }
    }
}