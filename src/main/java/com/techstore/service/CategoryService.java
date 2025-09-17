package com.techstore.service;

import com.techstore.dto.CategoryResponseDTO;
import com.techstore.dto.request.CategoryRequestDto;
import com.techstore.dto.response.CategorySummaryDTO;
import com.techstore.entity.Category;
import com.techstore.entity.SyncLog;
import com.techstore.exception.BusinessLogicException;
import com.techstore.exception.DuplicateResourceException;
import com.techstore.exception.ValidationException;
import com.techstore.repository.CategoryRepository;
import com.techstore.repository.SyncLogRepository;
import com.techstore.util.ExceptionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final SyncLogRepository syncLogRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponseDTO> getAllCategories() {
        log.debug("Fetching all active categories");

        return ExceptionHelper.wrapDatabaseOperation(() ->
                        categoryRepository.findByShowTrueOrderBySortOrderAscNameEnAsc()
                                .stream()
                                .map(this::convertToResponseDTO)
                                .collect(Collectors.toList()),
                "fetch all categories"
        );
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponseDTO> getAllCategories(Pageable pageable) {
        log.debug("Fetching paginated categories - Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());

        return ExceptionHelper.wrapDatabaseOperation(() ->
                        categoryRepository.findByShowTrue(pageable).map(this::convertToResponseDTO),
                "fetch paginated categories"
        );
    }

    @Transactional(readOnly = true)
    public CategoryResponseDTO getCategoryById(Long id) {
        log.debug("Fetching category with id: {}", id);

        validateCategoryId(id);

        Category category = findCategoryByIdOrThrow(id);
        return convertToResponseDTO(category);
    }

    @Transactional(readOnly = true)
    public CategoryResponseDTO getCategoryBySlug(String slug) {
        log.debug("Fetching category with slug: {}", slug);

        validateSlug(slug);

        Category category = ExceptionHelper.findOrThrow(
                categoryRepository.findBySlug(slug).orElse(null),
                "Category",
                "slug: " + slug
        );

        return convertToResponseDTO(category);
    }

    public CategoryResponseDTO createCategory(CategoryRequestDto requestDto) {
        log.info("Creating category with external ID: {}", requestDto.getId());

        String context = ExceptionHelper.createErrorContext(
                "createCategory", "Category", requestDto.getId(), null);

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            // Validate request
            validateCategoryRequest(requestDto, true);

            // Check for duplicates
            checkForDuplicateCategory(requestDto);

            // Create sync log
            SyncLog syncLog = createSyncLog("CATEGORIES");
            long startTime = System.currentTimeMillis();

            try {
                Category category = createCategoryFromRequest(requestDto);
                category = categoryRepository.save(category);

                updateSyncLogSuccess(syncLog, 1L, 1L, 0L, startTime);

                log.info("Category created successfully with id: {} and external id: {}",
                        category.getId(), category.getExternalId());

                return convertToResponseDTO(category);

            } catch (Exception e) {
                updateSyncLogError(syncLog, e.getMessage(), startTime);
                throw e;
            }
        }, context);
    }

    public CategoryResponseDTO updateCategory(Long id, CategoryRequestDto requestDTO) {
        log.info("Updating category with id: {}", id);

        String context = ExceptionHelper.createErrorContext("updateCategory", "Category", id, null);

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            // Validate inputs
            validateCategoryId(id);
            validateCategoryRequest(requestDTO, false);

            // Find existing category
            Category existingCategory = findCategoryByIdOrThrow(id);

            // Check slug uniqueness if changed
            if (requestDTO.getSlug() != null && !requestDTO.getSlug().equals(existingCategory.getSlug())) {
                checkSlugUniqueness(requestDTO.getSlug(), id);
            }

            // Update category
            updateCategoryFromRequest(existingCategory, requestDTO);
            Category updatedCategory = categoryRepository.save(existingCategory);

            log.info("Category updated successfully with id: {}", id);
            return convertToResponseDTO(updatedCategory);

        }, context);
    }

    public void deleteCategory(Long id) {
        log.info("Deleting category with id: {}", id);

        String context = ExceptionHelper.createErrorContext("deleteCategory", "Category", id, null);

        ExceptionHelper.wrapDatabaseOperation(() -> {
            validateCategoryId(id);

            Category category = findCategoryByIdOrThrow(id);

            // Business validation for deletion
            validateCategoryDeletion(category);

            // Soft delete
            category.setShow(false);
            categoryRepository.save(category);

            log.info("Category soft deleted successfully with id: {}", id);
            return null;
        }, context);
    }

    // ========== PRIVATE VALIDATION METHODS ==========

    private void validateCategoryId(Long id) {
        if (id == null || id <= 0) {
            throw new ValidationException("Category ID must be a positive number");
        }
    }

    private void validateSlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            throw new ValidationException("Category slug cannot be empty");
        }

        if (slug.length() > 200) {
            throw new ValidationException("Category slug cannot exceed 200 characters");
        }

        // Basic slug format validation
        if (!slug.matches("^[a-z0-9-]+$")) {
            throw new ValidationException("Category slug can only contain lowercase letters, numbers, and hyphens");
        }
    }

    private void validateCategoryRequest(CategoryRequestDto requestDto, boolean isCreate) {
        if (requestDto == null) {
            throw new ValidationException("Category request cannot be null");
        }

        if (isCreate && requestDto.getId() == null) {
            throw new ValidationException("External ID is required for category creation");
        }

        if (requestDto.getName() == null || requestDto.getName().isEmpty()) {
            throw new ValidationException("Category name is required");
        }

        // Validate name entries
        boolean hasValidName = requestDto.getName().stream()
                .anyMatch(name -> StringUtils.hasText(name.getText()));

        if (!hasValidName) {
            throw new ValidationException("At least one category name (EN or BG) must be provided");
        }

        // Validate name lengths
        requestDto.getName().forEach(name -> {
            if (StringUtils.hasText(name.getText()) && name.getText().length() > 200) {
                throw new ValidationException(
                        String.format("Category name (%s) cannot exceed 200 characters", name.getLanguageCode()));
            }
        });

        if (requestDto.getOrder() != null && requestDto.getOrder() < 0) {
            throw new ValidationException("Category order cannot be negative");
        }
    }

    private void validateCategoryDeletion(Category category) {
        // Check if category has subcategories
        if (category.getChildren() != null && !category.getChildren().isEmpty()) {
            long activeChildren = category.getChildren().stream()
                    .mapToLong(child -> child.getShow() ? 1 : 0)
                    .sum();

            if (activeChildren > 0) {
                throw new BusinessLogicException(
                        "Cannot delete category with active subcategories. Please delete or reassign subcategories first.");
            }
        }

        // Check if category has products
        if (category.getProducts() != null && !category.getProducts().isEmpty()) {
            long activeProducts = category.getProducts().stream()
                    .mapToLong(product -> product.getActive() ? 1 : 0)
                    .sum();

            if (activeProducts > 0) {
                throw new BusinessLogicException(
                        "Cannot delete category with active products. Please reassign or delete products first.");
            }
        }
    }

    // ========== PRIVATE HELPER METHODS ==========

    private Category findCategoryByIdOrThrow(Long id) {
        return ExceptionHelper.findOrThrow(
                categoryRepository.findById(id).orElse(null),
                "Category",
                id
        );
    }

    private void checkForDuplicateCategory(CategoryRequestDto requestDto) {
        if (requestDto.getId() != null && categoryRepository.findByExternalId(requestDto.getId()).isPresent()) {
            throw new DuplicateResourceException(
                    "Category already exists with external ID: " + requestDto.getId());
        }
    }

    private void checkSlugUniqueness(String slug, Long excludeId) {
        if (categoryRepository.existsBySlugAndIdNot(slug, excludeId)) {
            throw new DuplicateResourceException(
                    "Category with slug '" + slug + "' already exists");
        }
    }

    private Category createCategoryFromRequest(CategoryRequestDto requestDto) {
        Category category = new Category();
        category.setExternalId(requestDto.getId());
        category.setShow(requestDto.getShow() != null ? requestDto.getShow() : true);
        category.setSortOrder(requestDto.getOrder() != null ? requestDto.getOrder() : 0);

        // Set names
        setNamesFromRequest(category, requestDto);

        // Generate slug if not provided
        if (StringUtils.hasText(requestDto.getSlug())) {
            category.setSlug(requestDto.getSlug());
        } else {
            String baseName = category.getNameEn() != null ? category.getNameEn() : category.getNameBg();
            category.setSlug(generateSlug(baseName));
        }

        // Set parent if specified
        if (requestDto.getParent() != null && requestDto.getParent() != 0) {
            Category parent = findCategoryByExternalIdOrThrow(requestDto.getParent());
            category.setParent(parent);
        }

        return category;
    }

    private void updateCategoryFromRequest(Category category, CategoryRequestDto requestDto) {
        if (requestDto.getShow() != null) {
            category.setShow(requestDto.getShow());
        }

        if (requestDto.getOrder() != null) {
            category.setSortOrder(requestDto.getOrder());
        }

        if (requestDto.getName() != null) {
            setNamesFromRequest(category, requestDto);
        }

        if (StringUtils.hasText(requestDto.getSlug())) {
            category.setSlug(requestDto.getSlug());
        }

        if (requestDto.getParent() != null) {
            if (requestDto.getParent() == 0) {
                category.setParent(null);
            } else {
                Category parent = findCategoryByExternalIdOrThrow(requestDto.getParent());
                category.setParent(parent);
            }
        }
    }

    private void setNamesFromRequest(Category category, CategoryRequestDto requestDto) {
        if (requestDto.getName() != null) {
            requestDto.getName().forEach(name -> {
                if ("bg".equalsIgnoreCase(name.getLanguageCode())) {
                    category.setNameBg(name.getText());
                } else if ("en".equalsIgnoreCase(name.getLanguageCode())) {
                    category.setNameEn(name.getText());
                }
            });
        }
    }

    private Category findCategoryByExternalIdOrThrow(Long externalId) {
        return ExceptionHelper.findOrThrow(
                categoryRepository.findByExternalId(externalId).orElse(null),
                "Parent Category",
                "external ID: " + externalId
        );
    }

    private String generateSlug(String name) {
        if (!StringUtils.hasText(name)) {
            return "category-" + System.currentTimeMillis();
        }

        return name.toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    // ========== SYNC LOG METHODS ==========

    private SyncLog createSyncLog(String syncType) {
        try {
            SyncLog syncLog = new SyncLog();
            syncLog.setSyncType(syncType);
            syncLog.setStatus("IN_PROGRESS");
            return syncLogRepository.save(syncLog);
        } catch (Exception e) {
            log.error("Failed to create sync log: {}", e.getMessage());
            // Return dummy log to avoid breaking main operation
            SyncLog dummyLog = new SyncLog();
            dummyLog.setId(-1L);
            dummyLog.setSyncType(syncType);
            return dummyLog;
        }
    }

    private void updateSyncLogSuccess(SyncLog syncLog, long processed, long created, long updated, long startTime) {
        try {
            if (syncLog.getId() != null && syncLog.getId() > 0) {
                syncLog.setStatus("SUCCESS");
                syncLog.setRecordsProcessed(processed);
                syncLog.setRecordsCreated(created);
                syncLog.setRecordsUpdated(updated);
                syncLog.setDurationMs(System.currentTimeMillis() - startTime);
                syncLogRepository.save(syncLog);
            }
        } catch (Exception e) {
            log.error("Failed to update sync log: {}", e.getMessage());
        }
    }

    private void updateSyncLogError(SyncLog syncLog, String errorMessage, long startTime) {
        try {
            if (syncLog.getId() != null && syncLog.getId() > 0) {
                syncLog.setStatus("FAILED");
                syncLog.setErrorMessage(errorMessage);
                syncLog.setDurationMs(System.currentTimeMillis() - startTime);
                syncLogRepository.save(syncLog);
            }
        } catch (Exception e) {
            log.error("Failed to update sync log with error: {}", e.getMessage());
        }
    }

    // ========== CONVERSION METHODS ==========

    private CategoryResponseDTO convertToResponseDTO(Category category) {
        return CategoryResponseDTO.builder()
                .id(category.getId())
                .externalId(category.getExternalId())
                .nameEn(category.getNameEn())
                .nameBg(category.getNameBg())
                .slug(category.getSlug())
                .show(category.getShow())
                .sortOrder(category.getSortOrder())
                .parent(category.getParent() != null ? convertToSummaryDTO(category.getParent()) : null)
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .isParentCategory(category.isParentCategory())
                .build();
    }

    private CategorySummaryDTO convertToSummaryDTO(Category category) {
        return CategorySummaryDTO.builder()
                .id(category.getId())
                .nameEn(category.getNameEn())
                .nameBg(category.getNameBg())
                .slug(category.getSlug())
                .show(category.getShow())
                .build();
    }
}