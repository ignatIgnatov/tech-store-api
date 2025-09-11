package com.techstore.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techstore.dto.CategoryFilterDTO;
import com.techstore.dto.CategorySpecificationTemplateDTO;
import com.techstore.dto.NumericRange;
import com.techstore.dto.RangeDTO;
import com.techstore.dto.SpecificationFilterDTO;
import com.techstore.entity.Category;
import com.techstore.entity.CategorySpecificationTemplate;
import com.techstore.exception.DuplicateResourceException;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.repository.CategoryRepository;
import com.techstore.repository.CategorySpecificationTemplateRepository;
import com.techstore.repository.ProductSpecificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CategorySpecificationService {

    private final CategorySpecificationTemplateRepository templateRepository;
    private final CategoryRepository categoryRepository;
    private final ProductSpecificationRepository specificationRepository;
    private final ObjectMapper objectMapper;

    public CategorySpecificationTemplateDTO createTemplate(CategorySpecificationTemplateDTO dto) {
        if (templateRepository.existsByCategoryIdAndSpecName(dto.getCategoryId(), dto.getSpecName())) {
            throw new DuplicateResourceException("Specification template already exists for this category");
        }

        Category category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        CategorySpecificationTemplate template = convertToEntity(dto);
        template.setCategory(category);
        template = templateRepository.save(template);

        return convertToDTO(template);
    }

    public List<CategorySpecificationTemplateDTO> getCategoryTemplates(Long categoryId) {
        return templateRepository.findByCategoryIdOrderBySortOrderAscSpecNameAsc(categoryId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public CategoryFilterDTO getCategoryFilters(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        List<SpecificationFilterDTO> filters = templateRepository
                .findByCategoryIdAndFilterableTrueOrderBySortOrderAsc(categoryId)
                .stream()
                .map(this::createFilterFromTemplate)
                .collect(Collectors.toList());

        return CategoryFilterDTO.builder()
                .categoryId(categoryId)
                .categoryName(category.getNameEn())
                .filters(filters)
                .build();
    }

    private SpecificationFilterDTO createFilterFromTemplate(CategorySpecificationTemplate template) {
        SpecificationFilterDTO filter = SpecificationFilterDTO.builder()
                .templateId(template.getId())
                .specName(template.getSpecName())
                .specGroup(template.getSpecGroup())
                .type(template.getType())
                .unit(template.getSpecUnit())
                .build();

        switch (template.getType()) {
            case DROPDOWN:
            case MULTI_SELECT:
                filter.setAvailableValues(parseAllowedValues(template.getAllowedValues()));
                break;
            case NUMBER:
            case DECIMAL:
                NumericRange range = specificationRepository.findNumericRangeByTemplateId(template.getId());
                if (range.getMin() != null && range.getMax() != null) {
                    filter.setNumericRange(RangeDTO.builder()
                            .min(range.getMin())
                            .max(range.getMax())
                            .build());
                }
                break;
            default:
                filter.setAvailableValues(specificationRepository.findDistinctValuesByTemplateId(template.getId()));
        }

        return filter;
    }

    private List<String> parseAllowedValues(String allowedValuesJson) {
        try {
            return objectMapper.readValue(allowedValuesJson, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // Helper conversion methods
    private CategorySpecificationTemplate convertToEntity(CategorySpecificationTemplateDTO dto) {
        CategorySpecificationTemplate template = new CategorySpecificationTemplate();
        template.setSpecName(dto.getSpecName());
        template.setSpecUnit(dto.getSpecUnit());
        template.setSpecGroup(dto.getSpecGroup());
        template.setRequired(dto.getRequired());
        template.setFilterable(dto.getFilterable());
        template.setSearchable(dto.getSearchable());
        template.setSortOrder(dto.getSortOrder());
        template.setType(dto.getType());
        template.setDescription(dto.getDescription());
        template.setPlaceholder(dto.getPlaceholder());
        template.setShowInListing(dto.getShowInListing());
        template.setShowInComparison(dto.getShowInComparison());

        if (dto.getAllowedValues() != null && !dto.getAllowedValues().isEmpty()) {
            try {
                template.setAllowedValues(objectMapper.writeValueAsString(dto.getAllowedValues()));
            } catch (Exception e) {
                log.error("Error serializing allowed values", e);
            }
        }

        return template;
    }

    private CategorySpecificationTemplateDTO convertToDTO(CategorySpecificationTemplate template) {
        return CategorySpecificationTemplateDTO.builder()
                .id(template.getId())
                .specName(template.getSpecName())
                .specUnit(template.getSpecUnit())
                .specGroup(template.getSpecGroup())
                .required(template.getRequired())
                .filterable(template.getFilterable())
                .searchable(template.getSearchable())
                .sortOrder(template.getSortOrder())
                .type(template.getType())
                .description(template.getDescription())
                .placeholder(template.getPlaceholder())
                .showInListing(template.getShowInListing())
                .showInComparison(template.getShowInComparison())
                .allowedValues(parseAllowedValues(template.getAllowedValues()))
                .categoryId(template.getCategory().getId())
                .build();
    }
}
