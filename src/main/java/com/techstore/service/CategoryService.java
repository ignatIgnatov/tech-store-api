package com.techstore.service;

import com.techstore.dto.*;
import com.techstore.entity.Category;
import com.techstore.repository.CategoryRepository;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.exception.DuplicateResourceException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponseDTO> getAllCategories() {
        return categoryRepository.findByActiveTrueOrderBySortOrderAscNameEnAsc()
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponseDTO> getAllCategories(Pageable pageable) {
        return categoryRepository.findByActiveTrue(pageable)
                .map(this::convertToResponseDTO);
    }

    @Transactional(readOnly = true)
    public CategoryResponseDTO getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
        return convertToResponseDTO(category);
    }

    @Transactional(readOnly = true)
    public CategoryResponseDTO getCategoryBySlug(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with slug: " + slug));
        return convertToResponseDTO(category);
    }

    @Transactional(readOnly = true)
    public List<CategoryTreeDTO> getCategoryTree() {
        List<Category> parentCategories = categoryRepository.findByActiveTrueAndParentIsNullOrderBySortOrderAscNameEnAsc();
        return parentCategories.stream()
                .map(this::convertToTreeDTO)
                .collect(Collectors.toList());
    }

    public CategoryResponseDTO createCategory(CategoryRequestDTO requestDTO) {
        log.info("Creating new category with slug: {}", requestDTO.getSlug());

        if (categoryRepository.existsBySlug(requestDTO.getSlug())) {
            throw new DuplicateResourceException("Category with slug '" + requestDTO.getSlug() + "' already exists");
        }

        Category category = convertToEntity(requestDTO);
        category = categoryRepository.save(category);

        log.info("Category created successfully with id: {}", category.getId());
        return convertToResponseDTO(category);
    }

    public CategoryResponseDTO updateCategory(Long id, CategoryRequestDTO requestDTO) {
        log.info("Updating category with id: {}", id);

        Category existingCategory = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));

        if (categoryRepository.existsBySlugAndIdNot(requestDTO.getSlug(), id)) {
            throw new DuplicateResourceException("Category with slug '" + requestDTO.getSlug() + "' already exists");
        }

        updateCategoryFromDTO(existingCategory, requestDTO);
        Category updatedCategory = categoryRepository.save(existingCategory);

        log.info("Category updated successfully with id: {}", id);
        return convertToResponseDTO(updatedCategory);
    }

    public void deleteCategory(Long id) {
        log.info("Deleting category with id: {}", id);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));

        category.setActive(false);
        categoryRepository.save(category);

        log.info("Category soft deleted successfully with id: {}", id);
    }

    private Category convertToEntity(CategoryRequestDTO dto) {
        Category category = new Category();
        category.setNameEn(dto.getName());
        category.setSlug(dto.getSlug());
        category.setDescription(dto.getDescription());
        category.setImageUrl(dto.getImageUrl());
        category.setActive(dto.getActive());
        category.setSortOrder(dto.getSortOrder());

        if (dto.getParentId() != null) {
            Category parent = categoryRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category not found with id: " + dto.getParentId()));
            category.setParent(parent);
        }

        return category;
    }

    private void updateCategoryFromDTO(Category category, CategoryRequestDTO dto) {
        category.setNameEn(dto.getName());
        category.setSlug(dto.getSlug());
        category.setDescription(dto.getDescription());
        category.setImageUrl(dto.getImageUrl());
        category.setActive(dto.getActive());
        category.setSortOrder(dto.getSortOrder());

        if (dto.getParentId() != null) {
            Category parent = categoryRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent category not found with id: " + dto.getParentId()));
            category.setParent(parent);
        } else {
            category.setParent(null);
        }
    }

    private CategoryResponseDTO convertToResponseDTO(Category category) {
        return CategoryResponseDTO.builder()
                .id(category.getId())
                .name(category.getNameEn())
                .slug(category.getSlug())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .active(category.getActive())
                .sortOrder(category.getSortOrder())
                .parent(category.getParent() != null ? convertToSummaryDTO(category.getParent()) : null)
                .children(category.getChildren().stream()
                        .filter(Category::getActive)
                        .map(this::convertToSummaryDTO)
                        .collect(Collectors.toList()))
                .productCount(category.getProducts().size())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .fullPath(category.getFullPath())
                .isParentCategory(category.isParentCategory())
                .hasChildren(category.hasChildren())
                .build();
    }

    private CategorySummaryDTO convertToSummaryDTO(Category category) {
        return CategorySummaryDTO.builder()
                .id(category.getId())
                .name(category.getNameEn())
                .slug(category.getSlug())
                .active(category.getActive())
                .build();
    }

    private CategoryTreeDTO convertToTreeDTO(Category category) {
        return CategoryTreeDTO.builder()
                .id(category.getId())
                .name(category.getNameEn())
                .slug(category.getSlug())
                .active(category.getActive())
                .sortOrder(category.getSortOrder())
                .productCount(category.getProducts().size())
                .children(category.getChildren().stream()
                        .filter(Category::getActive)
                        .map(this::convertToTreeDTO)
                        .collect(Collectors.toList()))
                .build();
    }
}
