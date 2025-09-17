package com.techstore.service;

import com.techstore.dto.request.ParameterOptionRequestDto;
import com.techstore.dto.request.ParameterRequestDto;
import com.techstore.dto.response.ParameterResponseDto;
import com.techstore.entity.Category;
import com.techstore.entity.Parameter;
import com.techstore.entity.ParameterOption;
import com.techstore.exception.BusinessLogicException;
import com.techstore.exception.DuplicateResourceException;
import com.techstore.exception.ValidationException;
import com.techstore.mapper.ParameterMapper;
import com.techstore.repository.CategoryRepository;
import com.techstore.repository.ParameterOptionRepository;
import com.techstore.repository.ParameterRepository;
import com.techstore.util.ExceptionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ParameterService {

    private final ParameterRepository parameterRepository;
    private final ParameterMapper parameterMapper;
    private final ParameterOptionRepository parameterOptionRepository;
    private final CategoryRepository categoryRepository;

    @CacheEvict(value = "parameters", allEntries = true)
    public ParameterResponseDto createParameter(ParameterRequestDto requestDto, String language) {
        log.info("Creating parameter for category ID: {}", requestDto.getCategoryId());

        String context = ExceptionHelper.createErrorContext(
                "createParameter", "Parameter", requestDto.getId(),
                "category: " + requestDto.getCategoryId());

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            // Comprehensive validation
            validateParameterRequest(requestDto, true);

            // Find and validate category
            Category category = findCategoryByIdOrThrow(requestDto.getCategoryId());

            // Check for duplicates
            checkForDuplicateParameter(requestDto, category);

            // Create parameter with options
            Parameter parameter = createParameterFromRequest(requestDto, category);
            parameter = parameterRepository.save(parameter);

            // Create parameter options
            createParameterOptions(parameter, requestDto.getOptions());

            log.info("Parameter created successfully with id: {} and external id: {}",
                    parameter.getId(), parameter.getExternalId());

            return parameterMapper.toResponseDto(parameter, language);

        }, context);
    }

    @CacheEvict(value = "parameters", allEntries = true)
    public ParameterResponseDto updateParameter(Long id, ParameterRequestDto requestDto, String language) {
        log.info("Updating parameter with ID: {}", id);

        String context = ExceptionHelper.createErrorContext("updateParameter", "Parameter", id, null);

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            // Validate inputs
            validateParameterId(id);
            validateParameterRequest(requestDto, false);

            // Find existing parameter
            Parameter existingParameter = findParameterByIdOrThrow(id);

            // Validate parameter name uniqueness within category if changed
            validateParameterNameUniqueness(requestDto, existingParameter);

            // Update parameter
            updateParameterFromRequest(existingParameter, requestDto);
            Parameter updatedParameter = parameterRepository.save(existingParameter);

            // Update parameter options
            updateParameterOptions(updatedParameter, requestDto.getOptions());

            log.info("Parameter updated successfully with ID: {}", id);
            return parameterMapper.toResponseDto(updatedParameter, language);

        }, context);
    }

    @CacheEvict(value = "parameters", allEntries = true)
    public void deleteParameter(Long parameterId) {
        log.info("Deleting parameter with ID: {}", parameterId);

        String context = ExceptionHelper.createErrorContext("deleteParameter", "Parameter", parameterId, null);

        ExceptionHelper.wrapDatabaseOperation(() -> {
            validateParameterId(parameterId);

            Parameter parameter = findParameterByIdOrThrow(parameterId);

            // Business validation for deletion
            validateParameterDeletion(parameter);

            // Delete parameter (cascade will handle options and product parameters)
            parameterRepository.delete(parameter);

            log.info("Parameter deleted successfully with ID: {}", parameterId);
            return null;
        }, context);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "parameters", key = "'category_' + #categoryId + '_' + #language")
    public List<ParameterResponseDto> getParametersByCategory(Long categoryId, String language) {
        log.debug("Fetching parameters for available products in category: {}", categoryId);

        validateCategoryId(categoryId);

        // Verify category exists
        findCategoryByIdOrThrow(categoryId);

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            List<Parameter> parameters = parameterRepository.findParametersForAvailableProductsByCategory(categoryId);
            return parameters.stream()
                    .map(parameter -> parameterMapper.toResponseDto(parameter, language))
                    .toList();
        }, "fetch parameters for category: " + categoryId);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "parameters", key = "'category_all_' + #categoryId + '_' + #language")
    public List<ParameterResponseDto> findByCategory(Long categoryId, String language) {
        log.debug("Fetching all parameters for category: {}", categoryId);

        validateCategoryId(categoryId);

        // Verify category exists
        findCategoryByIdOrThrow(categoryId);

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            List<Parameter> parameters = parameterRepository.findByCategoryIdOrderByOrderAsc(categoryId);
            return parameters.stream()
                    .map(parameter -> parameterMapper.toResponseDto(parameter, language))
                    .toList();
        }, "fetch all parameters for category: " + categoryId);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "parameters", key = "#id + '_' + #language")
    public ParameterResponseDto getParameterById(Long id, String language) {
        log.debug("Fetching parameter with ID: {}", id);

        validateParameterId(id);

        Parameter parameter = findParameterByIdOrThrow(id);
        return parameterMapper.toResponseDto(parameter, language);
    }

    @Transactional(readOnly = true)
    public List<ParameterResponseDto> getAllParameters(String language) {
        log.debug("Fetching all parameters for language: {}", language);

        validateLanguage(language);

        return ExceptionHelper.wrapDatabaseOperation(() ->
                        parameterRepository.findAll().stream()
                                .map(parameter -> parameterMapper.toResponseDto(parameter, language))
                                .toList(),
                "fetch all parameters"
        );
    }

    @Transactional(readOnly = true)
    public boolean parameterExistsInCategory(String parameterName, Long categoryId, String language) {
        if (!StringUtils.hasText(parameterName) || categoryId == null) {
            return false;
        }

        validateCategoryId(categoryId);

        Category category = findCategoryByIdOrThrow(categoryId);

        return "bg".equalsIgnoreCase(language) ?
                parameterRepository.existsByNameBgIgnoreCaseAndCategory(parameterName.trim(), category) :
                parameterRepository.existsByNameEnIgnoreCaseAndCategory(parameterName.trim(), category);
    }

    // ========== PRIVATE VALIDATION METHODS ==========

    private void validateParameterId(Long id) {
        if (id == null || id <= 0) {
            throw new ValidationException("Parameter ID must be a positive number");
        }
    }

    private void validateCategoryId(Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            throw new ValidationException("Category ID must be a positive number");
        }
    }

    private void validateLanguage(String language) {
        if (!StringUtils.hasText(language)) {
            throw new ValidationException("Language is required");
        }

        if (!language.matches("^(en|bg)$")) {
            throw new ValidationException("Language must be 'en' or 'bg'");
        }
    }

    private void validateParameterRequest(ParameterRequestDto requestDto, boolean isCreate) {
        if (requestDto == null) {
            throw new ValidationException("Parameter request cannot be null");
        }

        if (isCreate && requestDto.getId() == null) {
            throw new ValidationException("External ID is required for parameter creation");
        }

        if (requestDto.getCategoryId() == null) {
            throw new ValidationException("Category ID is required");
        }

        if (requestDto.getName() == null || requestDto.getName().isEmpty()) {
            throw new ValidationException("Parameter name is required");
        }

        // Validate parameter names
        validateParameterNames(requestDto.getName());

        // Validate order
        if (requestDto.getOrder() != null && requestDto.getOrder() < 0) {
            throw new ValidationException("Parameter order cannot be negative");
        }

        // Validate options if provided
        if (requestDto.getOptions() != null && !requestDto.getOptions().isEmpty()) {
            validateParameterOptions(requestDto.getOptions());
        }
    }

    private void validateParameterNames(List<com.techstore.dto.external.NameDto> names) {
        boolean hasValidName = false;

        for (var name : names) {
            if (StringUtils.hasText(name.getText())) {
                hasValidName = true;

                if (name.getText().trim().length() > 255) {
                    throw new ValidationException(
                            String.format("Parameter name (%s) cannot exceed 255 characters",
                                    name.getLanguageCode()));
                }

                if (name.getText().trim().length() < 2) {
                    throw new ValidationException(
                            String.format("Parameter name (%s) must be at least 2 characters long",
                                    name.getLanguageCode()));
                }
            }
        }

        if (!hasValidName) {
            throw new ValidationException("At least one parameter name (EN or BG) must be provided");
        }
    }

    private void validateParameterOptions(List<ParameterOptionRequestDto> options) {
        if (options.isEmpty()) {
            return; // Options are optional
        }

        Set<Long> externalIds = new java.util.HashSet<>();
        Set<Integer> orders = new java.util.HashSet<>();

        for (ParameterOptionRequestDto option : options) {
            if (option.getId() == null) {
                throw new ValidationException("Parameter option external ID is required");
            }

            if (externalIds.contains(option.getId())) {
                throw new ValidationException("Duplicate parameter option external ID: " + option.getId());
            }
            externalIds.add(option.getId());

            if (option.getOrder() != null) {
                if (option.getOrder() < 0) {
                    throw new ValidationException("Parameter option order cannot be negative");
                }

                if (orders.contains(option.getOrder())) {
                    throw new ValidationException("Duplicate parameter option order: " + option.getOrder());
                }
                orders.add(option.getOrder());
            }

            if (option.getName() == null || option.getName().isEmpty()) {
                throw new ValidationException("Parameter option name is required");
            }

            validateParameterOptionNames(option.getName());
        }
    }

    private void validateParameterOptionNames(List<com.techstore.dto.external.NameDto> names) {
        boolean hasValidName = false;

        for (var name : names) {
            if (StringUtils.hasText(name.getText())) {
                hasValidName = true;

                if (name.getText().trim().length() > 255) {
                    throw new ValidationException(
                            String.format("Parameter option name (%s) cannot exceed 255 characters",
                                    name.getLanguageCode()));
                }

                if (name.getText().trim().length() < 1) {
                    throw new ValidationException(
                            String.format("Parameter option name (%s) cannot be empty",
                                    name.getLanguageCode()));
                }
            }
        }

        if (!hasValidName) {
            throw new ValidationException("At least one parameter option name (EN or BG) must be provided");
        }
    }

    private void validateParameterDeletion(Parameter parameter) {
        // Check if parameter has product parameters (is being used by products)
        if (parameter.getOptions() != null) {
            long totalProductUsages = parameter.getOptions().stream()
                    .mapToLong(option -> option.getProductParameters() != null ?
                            option.getProductParameters().size() : 0)
                    .sum();

            if (totalProductUsages > 0) {
                throw new BusinessLogicException(
                        String.format("Cannot delete parameter '%s' because it is used by %d products. " +
                                        "Please remove the parameter from products first.",
                                getParameterDisplayName(parameter), totalProductUsages));
            }
        }
    }

    private void validateParameterNameUniqueness(ParameterRequestDto requestDto, Parameter existingParameter) {
        if (requestDto.getName() == null || requestDto.getName().isEmpty()) {
            return;
        }

        // Check if names are actually changing
        boolean nameChanged = false;

        for (var nameDto : requestDto.getName()) {
            String newName = nameDto.getText() != null ? nameDto.getText().trim() : null;
            String currentName = null;

            if ("bg".equalsIgnoreCase(nameDto.getLanguageCode())) {
                currentName = existingParameter.getNameBg();
            } else if ("en".equalsIgnoreCase(nameDto.getLanguageCode())) {
                currentName = existingParameter.getNameEn();
            }

            if (!java.util.Objects.equals(newName, currentName)) {
                nameChanged = true;
                break;
            }
        }

        if (nameChanged) {
            checkForDuplicateParameterNames(requestDto, existingParameter.getCategory());
        }
    }

    // ========== PRIVATE HELPER METHODS ==========

    private Parameter findParameterByIdOrThrow(Long id) {
        return ExceptionHelper.findOrThrow(
                parameterRepository.findById(id).orElse(null),
                "Parameter",
                id
        );
    }

    private Category findCategoryByIdOrThrow(Long categoryId) {
        return ExceptionHelper.findOrThrow(
                categoryRepository.findById(categoryId).orElse(null),
                "Category",
                categoryId
        );
    }

    private void checkForDuplicateParameter(ParameterRequestDto requestDto, Category category) {
        // Check by external ID if this is a sync operation
        if (requestDto.getId() != null) {
            if (parameterRepository.findByExternalIdAndCategoryId(requestDto.getId(), category.getId()).isPresent()) {
                throw new DuplicateResourceException(
                        String.format("Parameter already exists with external ID %d in category %d",
                                requestDto.getId(), category.getId()));
            }
        }

        // Check by names
        checkForDuplicateParameterNames(requestDto, category);
    }

    private void checkForDuplicateParameterNames(ParameterRequestDto requestDto, Category category) {
        for (var nameDto : requestDto.getName()) {
            if (!StringUtils.hasText(nameDto.getText())) {
                continue;
            }

            String parameterName = nameDto.getText().trim();

            if ("bg".equalsIgnoreCase(nameDto.getLanguageCode())) {
                if (parameterRepository.existsByNameBgIgnoreCaseAndCategory(parameterName, category)) {
                    throw new DuplicateResourceException(
                            String.format("Parameter with Bulgarian name '%s' already exists in this category",
                                    parameterName));
                }
            } else if ("en".equalsIgnoreCase(nameDto.getLanguageCode())) {
                if (parameterRepository.existsByNameEnIgnoreCaseAndCategory(parameterName, category)) {
                    throw new DuplicateResourceException(
                            String.format("Parameter with English name '%s' already exists in this category",
                                    parameterName));
                }
            }
        }
    }

    private Parameter createParameterFromRequest(ParameterRequestDto requestDto, Category category) {
        Parameter parameter = new Parameter();
        parameter.setExternalId(requestDto.getId());
        parameter.setCategory(category);
        parameter.setOrder(requestDto.getOrder() != null ? requestDto.getOrder() : 0);

        setParameterNamesFromRequest(parameter, requestDto.getName());

        return parameter;
    }

    private void updateParameterFromRequest(Parameter parameter, ParameterRequestDto requestDto) {
        if (requestDto.getOrder() != null) {
            parameter.setOrder(requestDto.getOrder());
        }

        if (requestDto.getName() != null && !requestDto.getName().isEmpty()) {
            setParameterNamesFromRequest(parameter, requestDto.getName());
        }
    }

    private void setParameterNamesFromRequest(Parameter parameter, List<com.techstore.dto.external.NameDto> names) {
        for (var nameDto : names) {
            if (StringUtils.hasText(nameDto.getText())) {
                if ("bg".equalsIgnoreCase(nameDto.getLanguageCode())) {
                    parameter.setNameBg(nameDto.getText().trim());
                } else if ("en".equalsIgnoreCase(nameDto.getLanguageCode())) {
                    parameter.setNameEn(nameDto.getText().trim());
                }
            }
        }
    }

    private void createParameterOptions(Parameter parameter, List<ParameterOptionRequestDto> optionDtos) {
        if (optionDtos == null || optionDtos.isEmpty()) {
            return;
        }

        List<ParameterOption> options = new ArrayList<>();

        for (ParameterOptionRequestDto optionDto : optionDtos) {
            ParameterOption option = createParameterOptionFromRequest(optionDto, parameter);
            options.add(option);
        }

        parameterOptionRepository.saveAll(options);
        log.debug("Created {} parameter options for parameter: {}", options.size(), parameter.getId());
    }

    private void updateParameterOptions(Parameter parameter, List<ParameterOptionRequestDto> optionDtos) {
        if (optionDtos == null) {
            return; // Don't update options if not provided
        }

        // Get existing options
        Map<Long, ParameterOption> existingOptions = parameterOptionRepository
                .findByParameterIdOrderByOrderAsc(parameter.getId())
                .stream()
                .collect(Collectors.toMap(ParameterOption::getExternalId, option -> option));

        // Process provided options
        List<ParameterOption> optionsToSave = new ArrayList<>();
        Set<Long> processedExternalIds = new java.util.HashSet<>();

        for (ParameterOptionRequestDto optionDto : optionDtos) {
            ParameterOption option = existingOptions.get(optionDto.getId());

            if (option == null) {
                // Create new option
                option = createParameterOptionFromRequest(optionDto, parameter);
            } else {
                // Update existing option
                updateParameterOptionFromRequest(option, optionDto);
            }

            optionsToSave.add(option);
            processedExternalIds.add(optionDto.getId());
        }

        // Delete options that are no longer in the request
        List<ParameterOption> optionsToDelete = existingOptions.values().stream()
                .filter(option -> !processedExternalIds.contains(option.getExternalId()))
                .collect(Collectors.toList());

        if (!optionsToDelete.isEmpty()) {
            parameterOptionRepository.deleteAll(optionsToDelete);
            log.debug("Deleted {} parameter options for parameter: {}", optionsToDelete.size(), parameter.getId());
        }

        parameterOptionRepository.saveAll(optionsToSave);
        log.debug("Updated {} parameter options for parameter: {}", optionsToSave.size(), parameter.getId());
    }

    private ParameterOption createParameterOptionFromRequest(ParameterOptionRequestDto optionDto, Parameter parameter) {
        ParameterOption option = new ParameterOption();
        option.setExternalId(optionDto.getId());
        option.setParameter(parameter);
        option.setOrder(optionDto.getOrder() != null ? optionDto.getOrder() : 0);

        setParameterOptionNamesFromRequest(option, optionDto.getName());

        return option;
    }

    private void updateParameterOptionFromRequest(ParameterOption option, ParameterOptionRequestDto optionDto) {
        if (optionDto.getOrder() != null) {
            option.setOrder(optionDto.getOrder());
        }

        if (optionDto.getName() != null && !optionDto.getName().isEmpty()) {
            setParameterOptionNamesFromRequest(option, optionDto.getName());
        }
    }

    private void setParameterOptionNamesFromRequest(ParameterOption option, List<com.techstore.dto.external.NameDto> names) {
        for (var nameDto : names) {
            if (StringUtils.hasText(nameDto.getText())) {
                if ("bg".equalsIgnoreCase(nameDto.getLanguageCode())) {
                    option.setNameBg(nameDto.getText().trim());
                } else if ("en".equalsIgnoreCase(nameDto.getLanguageCode())) {
                    option.setNameEn(nameDto.getText().trim());
                }
            }
        }
    }

    private String getParameterDisplayName(Parameter parameter) {
        if (StringUtils.hasText(parameter.getNameEn())) {
            return parameter.getNameEn();
        } else if (StringUtils.hasText(parameter.getNameBg())) {
            return parameter.getNameBg();
        } else {
            return "Parameter ID: " + parameter.getId();
        }
    }
}