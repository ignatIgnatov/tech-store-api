package com.techstore.service;

import com.techstore.dto.external.ImageDto;
import com.techstore.dto.request.CategoryRequestFromExternalDto;
import com.techstore.dto.request.ManufacturerRequestDto;
import com.techstore.dto.request.ParameterOptionRequestDto;
import com.techstore.dto.request.ParameterRequestDto;
import com.techstore.dto.request.ParameterValueRequestDto;
import com.techstore.dto.request.ProductRequestDto;
import com.techstore.entity.Category;
import com.techstore.entity.Manufacturer;
import com.techstore.entity.Parameter;
import com.techstore.entity.ParameterOption;
import com.techstore.entity.Product;
import com.techstore.entity.ProductParameter;
import com.techstore.entity.SyncLog;
import com.techstore.enums.ProductStatus;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.repository.CategoryRepository;
import com.techstore.repository.ManufacturerRepository;
import com.techstore.repository.ParameterOptionRepository;
import com.techstore.repository.ParameterRepository;
import com.techstore.repository.ProductRepository;
import com.techstore.repository.SyncLogRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncService {

    private static final String LOG_STATUS_SUCCESS = "SUCCESS";
    private static final String LOG_STATUS_FAILED = "FAILED";
    private static final String LOG_STATUS_IN_PROGRESS = "IN_PROGRESS";

    private final ValiApiService valiApiService;
    private final CategoryRepository categoryRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final ProductRepository productRepository;
    private final ParameterRepository parameterRepository;
    private final ParameterOptionRepository parameterOptionRepository;
    private final SyncLogRepository syncLogRepository;
    private final EntityManager entityManager;
    private final CachedLookupService cachedLookupService;
    private final TekraApiService tekraApiService;

    @Value("#{'${excluded.categories.external-ids}'.split(',')}")
    private Set<Long> excludedCategories;

    @Value("${app.sync.batch-size:30}")
    private int batchSize;

    @Value("${app.sync.max-chunk-duration-minutes:5}")
    private int maxChunkDurationMinutes;

    // ============ VALI API SYNC METHODS ============

    @Transactional
    public void syncCategories() {
        SyncLog syncLog = createSyncLogSimple("CATEGORIES");
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting categories synchronization");

            List<CategoryRequestFromExternalDto> externalCategories = valiApiService.getCategories();

            Map<Long, Category> existingCategories = cachedLookupService.getAllCategoriesMap();

            long created = 0, updated = 0, skipped = 0;

            for (CategoryRequestFromExternalDto extCategory : externalCategories) {
                if (excludedCategories.contains(extCategory.getId())) {
                    skipped++;
                    continue;
                }

                Category category = existingCategories.get(extCategory.getId());

                if (category == null) {
                    category = createCategoryFromExternal(extCategory);
                    category = categoryRepository.save(category);
                    existingCategories.put(category.getExternalId(), category);
                    created++;
                } else {
                    updateCategoryFromExternal(category, extCategory);
                    category = categoryRepository.save(category);
                    existingCategories.put(category.getExternalId(), category);
                    updated++;
                }
            }

            updateCategoryParents(externalCategories, existingCategories);

            updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, externalCategories.size(), created, updated, 0,
                    skipped > 0 ? String.format("Skipped %d excluded categories", skipped) : null, startTime);
        } catch (Exception e) {
            updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, e.getMessage(), startTime);
            throw e;
        }
    }

    @Transactional
    public void syncManufacturers() {
        SyncLog syncLog = createSyncLogSimple("MANUFACTURERS");
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting manufacturers synchronization");

            List<ManufacturerRequestDto> externalManufacturers = valiApiService.getManufacturers();
            Map<Long, Manufacturer> existingManufacturers = cachedLookupService.getAllManufacturersMap();

            long created = 0, updated = 0;

            for (ManufacturerRequestDto extManufacturer : externalManufacturers) {
                Manufacturer manufacturer = existingManufacturers.get(extManufacturer.getId());

                if (manufacturer == null) {
                    manufacturer = createManufacturerFromExternal(extManufacturer);
                    manufacturer = manufacturerRepository.save(manufacturer);
                    existingManufacturers.put(manufacturer.getExternalId(), manufacturer);
                    created++;
                } else {
                    updateManufacturerFromExternal(manufacturer, extManufacturer);
                    manufacturer = manufacturerRepository.save(manufacturer);
                    existingManufacturers.put(manufacturer.getExternalId(), manufacturer);
                    updated++;
                }

                manufacturerRepository.save(manufacturer);
            }

            updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, (long) externalManufacturers.size(), created, updated, 0, null, startTime);
            log.info("Manufacturers synchronization completed - Created: {}, Updated: {}", created, updated);

        } catch (Exception e) {
            updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, e.getMessage(), startTime);
            log.error("Error during manufacturers synchronization", e);
            throw e;
        }
    }

    @Transactional
    public void syncParameters() {
        SyncLog syncLog = createSyncLogSimple("PARAMETERS");
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting Vali parameters synchronization with options");

            List<Category> categories = categoryRepository.findAll();
            long totalProcessed = 0, created = 0, updated = 0, errors = 0;

            for (Category category : categories) {
                try {
                    Map<String, Parameter> existingParameters = cachedLookupService.getParametersByCategory(category);
                    List<ParameterRequestDto> externalParameters = valiApiService.getParametersByCategory(category.getExternalId());

                    if (externalParameters.isEmpty()) {
                        log.debug("No parameters found for category: {}", category.getNameBg());
                        continue;
                    }

                    List<Parameter> parametersToSave = new ArrayList<>();

                    for (ParameterRequestDto extParam : externalParameters) {
                        Parameter parameter = existingParameters.get(extParam.getId().toString());

                        if (parameter == null) {
                            parameter = parameterRepository
                                    .findByExternalIdAndCategoryId(extParam.getId(), category.getId())
                                    .orElseGet(() -> createParameterFromExternal(extParam, category));
                            created++;
                        } else {
                            updateParameterFromExternal(parameter, extParam);
                            updated++;
                        }

                        parametersToSave.add(parameter);
                    }

                    if (!parametersToSave.isEmpty()) {
                        parameterRepository.saveAll(parametersToSave);

                        for (int i = 0; i < parametersToSave.size(); i++) {
                            Parameter parameter = parametersToSave.get(i);
                            ParameterRequestDto extParam = externalParameters.get(i);

                            try {
                                if (parameter.getExternalId() != null && extParam.getOptions() != null) {
                                    syncValiParameterOptions(parameter, extParam.getOptions());
                                }
                            } catch (Exception e) {
                                log.error("Error syncing options for parameter {}: {}",
                                        parameter.getNameBg(), e.getMessage());
                                errors++;
                            }
                        }
                    }

                    totalProcessed += externalParameters.size();

                } catch (Exception e) {
                    log.error("Error syncing parameters for category {}: {}", category.getExternalId(), e.getMessage());
                    errors++;
                }
            }

            String message = String.format("Vali parameters processed: %d, created: %d, updated: %d",
                    totalProcessed, created, updated);
            if (errors > 0) {
                message += String.format(", errors: %d", errors);
            }

            updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, totalProcessed, created, updated, errors,
                    errors > 0 ? message : null, startTime);

            log.info("Vali parameters synchronization completed - Processed: {}, Created: {}, Updated: {}, Errors: {}",
                    totalProcessed, created, updated, errors);

        } catch (Exception e) {
            updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, e.getMessage(), startTime);
            log.error("Error during Vali parameters synchronization", e);
            throw e;
        }
    }

    @Transactional
    public void syncProducts() {
        log.info("Starting chunked products synchronization");
        SyncLog syncLog = createSyncLogSimple("PRODUCTS");
        long startTime = System.currentTimeMillis();

        long totalProcessed = 0, created = 0, updated = 0, errors = 0;

        try {
            List<Category> categories = categoryRepository.findAll();
            log.info("Found {} categories to process for products", categories.size());

            for (Category category : categories) {
                try {
                    CategorySyncResult result = syncProductsByCategory(category);
                    totalProcessed += result.processed;
                    created += result.created;
                    updated += result.updated;
                    errors += result.errors;
                } catch (Exception e) {
                    log.error("Error processing products for category {}: {}", category.getExternalId(), e.getMessage());
                    errors++;
                }
            }

            updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, totalProcessed, created, updated, errors,
                    errors > 0 ? String.format("Completed with %d errors", errors) : null, startTime);
            log.info("Products synchronization completed - Created: {}, Updated: {}, Errors: {}", created, updated, errors);

        } catch (Exception e) {
            updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, totalProcessed, created, updated, errors, e.getMessage(), startTime);
            log.error("Error during products synchronization", e);
            throw e;
        }
    }

    // ============ TEKRA API SYNC METHODS ============

    @Transactional
    public void syncTekraCategories() {
        SyncLog syncLog = createSyncLogSimple("TEKRA_CATEGORIES");
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting Tekra categories synchronization with parent validation");

            List<Map<String, Object>> externalCategories = tekraApiService.getCategoriesRaw();

            if (externalCategories.isEmpty()) {
                log.warn("No categories returned from Tekra API");
                updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, 0, 0, 0, 0, "No categories found", startTime);
                return;
            }

            Map<String, Object> mainCategory = externalCategories.stream()
                    .filter(extCategory -> "videonablyudenie".equals(getString(extCategory, "slug")))
                    .findFirst()
                    .orElse(null);

            if (mainCategory == null) {
                log.warn("Category 'videonablyudenie' not found in Tekra API");
                updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, 0, 0, 0, 0, "Main category not found", startTime);
                return;
            }

            Map<String, Category> existingCategories = categoryRepository.findAll()
                    .stream()
                    .filter(cat -> cat.getSlug() != null)
                    .collect(Collectors.toMap(
                            Category::getSlug,
                            cat -> cat,
                            (existing, duplicate) -> existing
                    ));

            long created = 0, updated = 0, skipped = 0;

            // СТЪПКА 1: Създай главната категория
            log.info("=== STEP 1: Creating main category ===");
            Category mainCat = createOrUpdateTekraCategory(mainCategory, existingCategories, null);
            if (mainCat != null) {
                if (existingCategories.containsKey(mainCat.getSlug())) {
                    updated++;
                } else {
                    created++;
                    existingCategories.put(mainCat.getSlug(), mainCat);
                }
            }
            log.info("Main category created: ID={}", mainCat != null ? mainCat.getId() : "NULL");

            // СТЪПКА 2: Обработи level-2 категории
            log.info("=== STEP 2: Processing level-2 categories ===");
            Object subCategoriesObj = mainCategory.get("sub_categories");
            if (!(subCategoriesObj instanceof List)) {
                log.warn("No sub_categories found in main category");
                updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, 1, created, updated, 0, "No subcategories", startTime);
                return;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> subCategories = (List<Map<String, Object>>) subCategoriesObj;

            log.info("Found {} level-2 categories from Tekra", subCategories.size());

            // Map за бърз достъп до level-2 по tekra_slug
            Map<String, Category> level2Categories = new HashMap<>();

            for (int i = 0; i < subCategories.size(); i++) {
                Map<String, Object> subCat = subCategories.get(i);

                try {
                    String subCatSlug = getString(subCat, "slug");
                    String subCatName = getString(subCat, "name");

                    log.info("Processing level-2 [{}/{}]: '{}' (slug: {})",
                            i + 1, subCategories.size(), subCatName, subCatSlug);

                    if (subCatSlug == null || subCatName == null) {
                        log.warn("Skipping level-2 category with missing fields");
                        skipped++;
                        continue;
                    }

                    Category level2Cat = createOrUpdateTekraCategory(subCat, existingCategories, mainCat);
                    if (level2Cat != null) {
                        String level2Key = level2Cat.getSlug();

                        if (existingCategories.containsKey(level2Key)) {
                            updated++;
                        } else {
                            created++;
                            existingCategories.put(level2Key, level2Cat);
                        }

                        // ✅ Запазваме в map за level-3 обработка
                        level2Categories.put(subCatSlug, level2Cat);

                        log.info("✓ Created/Updated level-2: '{}' (ID: {}, path: {})",
                                level2Cat.getNameBg(), level2Cat.getId(), level2Cat.getCategoryPath());
                    } else {
                        log.error("Failed to create/update level-2 category: {}", subCatName);
                    }

                } catch (Exception e) {
                    log.error("ERROR processing level-2 category [{}]: {}", i + 1, e.getMessage(), e);
                    skipped++;
                }
            }

            log.info("=== STEP 2 COMPLETE: {} level-2 categories processed ===", level2Categories.size());
            log.info("level2Categories map contains: {}", level2Categories.keySet());

            // СТЪПКА 3: Обработи level-3 категории
            log.info("=== STEP 3: Processing level-3 categories ===");

            int totalLevel3 = 0;
            for (int i = 0; i < subCategories.size(); i++) {
                Map<String, Object> subCat = subCategories.get(i);

                try {
                    String subCatSlug = getString(subCat, "slug");
                    String subCatName = getString(subCat, "name");

                    log.info("Looking for level-3 under level-2 [{}/{}]: '{}' (slug: {})",
                            i + 1, subCategories.size(), subCatName, subCatSlug);

                    Category parentCategory = level2Categories.get(subCatSlug);

                    if (parentCategory == null) {
                        log.warn("✗ Parent category NOT FOUND in map for slug: '{}'. Available keys: {}",
                                subCatSlug, level2Categories.keySet());
                        continue;
                    }

                    log.info("✓ Found parent category: '{}' (ID: {})",
                            parentCategory.getNameBg(), parentCategory.getId());

                    Object subSubCatObj = subCat.get("subsubcat");
                    if (!(subSubCatObj instanceof List)) {
                        log.info("No level-3 categories under '{}'", subCatName);
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> subSubCategories = (List<Map<String, Object>>) subSubCatObj;

                    log.info("Processing {} level-3 categories under '{}'",
                            subSubCategories.size(), parentCategory.getNameBg());

                    for (int j = 0; j < subSubCategories.size(); j++) {
                        Map<String, Object> subSubCat = subSubCategories.get(j);

                        try {
                            String subSubCatSlug = getString(subSubCat, "slug");
                            String subSubCatName = getString(subSubCat, "name");

                            log.info("  Processing level-3 [{}/{}]: '{}' (slug: {})",
                                    j + 1, subSubCategories.size(), subSubCatName, subSubCatSlug);

                            if (subSubCatSlug == null || subSubCatName == null) {
                                log.warn("  Skipping level-3 with missing fields");
                                skipped++;
                                continue;
                            }

                            if (parentCategory.getId() == null) {
                                log.error("  Parent category '{}' has NULL ID! Skipping...",
                                        parentCategory.getNameBg());
                                skipped++;
                                continue;
                            }

                            log.info("  Creating level-3: '{}' under parent '{}' (ID: {})",
                                    subSubCatName, parentCategory.getNameBg(), parentCategory.getId());

                            Category level3Cat = createOrUpdateTekraCategory(
                                    subSubCat, existingCategories, parentCategory);

                            if (level3Cat != null) {
                                String level3Key = level3Cat.getSlug();

                                if (existingCategories.containsKey(level3Key)) {
                                    updated++;
                                } else {
                                    created++;
                                    existingCategories.put(level3Key, level3Cat);
                                }

                                totalLevel3++;

                                log.info("  ✓ Created/Updated level-3: '{}' (ID: {}, parent_id: {}, path: {})",
                                        level3Cat.getNameBg(),
                                        level3Cat.getId(),
                                        level3Cat.getParent() != null ? level3Cat.getParent().getId() : "NULL",
                                        level3Cat.getCategoryPath());
                            } else {
                                log.error("  Failed to create level-3: {}", subSubCatName);
                            }

                        } catch (Exception e) {
                            log.error("  ERROR processing level-3 category '{}': {}",
                                    getString(subSubCat, "name"), e.getMessage(), e);
                            skipped++;
                        }
                    }

                } catch (Exception e) {
                    log.error("ERROR processing subcategories for level-2 [{}]: {}", i + 1, e.getMessage(), e);
                    skipped++;
                }
            }

            log.info("=== STEP 3 COMPLETE: {} level-3 categories processed ===", totalLevel3);

            // Flush преди да завършим
            log.info("=== Flushing entity manager ===");
            entityManager.flush();
            entityManager.clear();
            log.info("✓ Flush complete");

            long totalCategories = created + updated;
            updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, totalCategories, created, updated, skipped,
                    skipped > 0 ? String.format("Skipped %d categories", skipped) : null, startTime);

            log.info("=== SYNC COMPLETE ===");
            log.info("Tekra categories sync completed - Total: {}, Created: {}, Updated: {}, Skipped: {}",
                    totalCategories, created, updated, skipped);

            // Валидация след sync
            validateCategoryHierarchy();

        } catch (Exception e) {
            log.error("=== SYNC FAILED ===", e);
            updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, e.getMessage(), startTime);
            log.error("Error during Tekra categories synchronization", e);
            throw e;
        }
    }

    @Transactional
    public void syncTekraManufacturers() {
        SyncLog syncLog = createSyncLogSimple("TEKRA_MANUFACTURERS");
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting Tekra manufacturers synchronization");

            List<Category> tekraCategories = categoryRepository.findAll().stream()
                    .filter(cat -> cat.getTekraSlug() != null)
                    .toList();

            if (tekraCategories.isEmpty()) {
                log.warn("No Tekra categories found");
                updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, 0, 0, 0, 0, "No Tekra categories found", startTime);
                return;
            }

            log.info("Extracting manufacturers from {} Tekra categories", tekraCategories.size());

            Set<String> allTekraManufacturers = new HashSet<>();

            for (Category category : tekraCategories) {
                try {
                    Set<String> categoryManufacturers = tekraApiService
                            .extractTekraManufacturersFromProducts(category.getTekraSlug());

                    allTekraManufacturers.addAll(categoryManufacturers);

                    log.info("Found {} manufacturers in category '{}'",
                            categoryManufacturers.size(), category.getNameBg());

                } catch (Exception e) {
                    log.error("Error extracting manufacturers from category '{}': {}",
                            category.getNameBg(), e.getMessage());
                }
            }

            if (allTekraManufacturers.isEmpty()) {
                log.warn("No manufacturers found in any Tekra products");
                updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, 0, 0, 0, 0, "No manufacturers found", startTime);
                return;
            }

            log.info("Found total of {} unique manufacturers across all categories", allTekraManufacturers.size());

            Map<String, Manufacturer> existingManufacturers = manufacturerRepository.findAll()
                    .stream()
                    .collect(Collectors.toMap(Manufacturer::getName, m -> m));

            long created = 0, updated = 0, errors = 0;

            for (String manufacturerName : allTekraManufacturers) {
                try {
                    Manufacturer manufacturer = existingManufacturers.get(manufacturerName);

                    if (manufacturer == null) {
                        manufacturer = createTekraManufacturer(manufacturerName);
                        if (manufacturer != null) {
                            manufacturer = manufacturerRepository.save(manufacturer);
                            existingManufacturers.put(manufacturer.getName(), manufacturer);
                            created++;
                            log.debug("Created Tekra manufacturer: {}", manufacturerName);
                        } else {
                            errors++;
                        }
                    } else {
                        updated++;
                        log.debug("Tekra manufacturer already exists: {}", manufacturerName);
                    }
                } catch (Exception e) {
                    errors++;
                    log.error("Error processing Tekra manufacturer {}: {}", manufacturerName, e.getMessage());
                }
            }

            updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, (long) allTekraManufacturers.size(),
                    created, updated, errors,
                    errors > 0 ? String.format("Completed with %d errors", errors) : null, startTime);

            log.info("Tekra manufacturers synchronization completed - Total: {}, Created: {}, Updated: {}, Errors: {}",
                    allTekraManufacturers.size(), created, updated, errors);

        } catch (Exception e) {
            updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, e.getMessage(), startTime);
            log.error("Error during Tekra manufacturers synchronization", e);
            throw e;
        }
    }

    @Transactional
    public void syncTekraParameters() {
        SyncLog syncLog = createSyncLogSimple("TEKRA_PARAMETERS");
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting Tekra parameters synchronization for all categories");

            List<Category> tekraCategories = categoryRepository.findAll().stream()
                    .filter(cat -> cat.getTekraSlug() != null)
                    .toList();

            if (tekraCategories.isEmpty()) {
                log.error("No Tekra categories found. Sync categories first.");
                updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, "No Tekra categories found", startTime);
                return;
            }

            log.info("Found {} Tekra categories to sync parameters for", tekraCategories.size());

            long totalProcessed = 0, totalCreated = 0, totalUpdated = 0, totalErrors = 0;
            long totalParameterOptionsCreated = 0, totalParameterOptionsUpdated = 0;

            for (Category category : tekraCategories) {
                try {
                    log.info("Processing parameters for category: {} (slug: {})",
                            category.getNameBg(), category.getTekraSlug());

                    Map<String, Set<String>> tekraParameters = tekraApiService
                            .extractTekraParametersFromProducts(category.getTekraSlug());

                    if (tekraParameters.isEmpty()) {
                        log.info("No parameters found for category: {}", category.getNameBg());
                        continue;
                    }

                    log.info("Extracted {} parameter types for category '{}'",
                            tekraParameters.size(), category.getNameBg());

                    Map<String, Parameter> existingParameters = parameterRepository.findByCategoryId(category.getId())
                            .stream()
                            .collect(Collectors.toMap(
                                    p -> p.getTekraKey() != null ? p.getTekraKey() : p.getNameBg(),
                                    p -> p,
                                    (existing, duplicate) -> existing
                            ));

                    long categoryParamsCreated = 0, categoryParamsUpdated = 0, categoryParamsErrors = 0;
                    long categoryOptionsCreated = 0, categoryOptionsUpdated = 0;

                    for (Map.Entry<String, Set<String>> paramEntry : tekraParameters.entrySet()) {
                        try {
                            String parameterKey = paramEntry.getKey();
                            Set<String> parameterValues = paramEntry.getValue();

                            log.debug("Processing parameter: {} with {} values for category {}",
                                    parameterKey, parameterValues.size(), category.getNameBg());

                            String parameterName = convertTekraParameterKeyToName(parameterKey);

                            Parameter parameter = existingParameters.get(parameterKey);
                            boolean isNewParameter = false;

                            if (parameter == null) {
                                parameter = parameterRepository.findByCategoryAndNameBg(category, parameterName)
                                        .orElse(null);
                            }

                            if (parameter == null) {
                                parameter = new Parameter();
                                parameter.setCategory(category);
                                parameter.setTekraKey(parameterKey);
                                parameter.setNameBg(parameterName);
                                parameter.setNameEn(translateParameterName(parameterName));
                                parameter.setOrder(getParameterOrder(parameterKey));
                                isNewParameter = true;
                            }

                            parameter = parameterRepository.save(parameter);

                            if (isNewParameter) {
                                categoryParamsCreated++;
                                existingParameters.put(parameterKey, parameter);
                                log.debug("Created parameter: {} ({}) for category {}",
                                        parameterName, parameterKey, category.getNameBg());
                            } else {
                                categoryParamsUpdated++;
                                log.debug("Parameter already exists: {} ({}) for category {}",
                                        parameterName, parameterKey, category.getNameBg());
                            }

                            Map<String, ParameterOption> existingOptions = parameterOptionRepository
                                    .findByParameterIdOrderByOrderAsc(parameter.getId())
                                    .stream()
                                    .collect(Collectors.toMap(ParameterOption::getNameBg, o -> o));

                            int orderCounter = 0;
                            for (String optionValue : parameterValues) {
                                try {
                                    ParameterOption option = existingOptions.get(optionValue);

                                    if (option == null) {
                                        option = new ParameterOption();
                                        option.setParameter(parameter);
                                        option.setNameBg(optionValue);
                                        option.setNameEn(optionValue);
                                        option.setOrder(orderCounter++);

                                        parameterOptionRepository.save(option);
                                        categoryOptionsCreated++;
                                        log.debug("Created parameter option: {} = {} for category {}",
                                                parameter.getNameBg(), optionValue, category.getNameBg());
                                    } else {
                                        categoryOptionsUpdated++;
                                    }
                                } catch (Exception e) {
                                    categoryParamsErrors++;
                                    log.error("Error processing parameter option {}: {}", optionValue, e.getMessage());
                                }
                            }

                            totalProcessed++;

                        } catch (Exception e) {
                            categoryParamsErrors++;
                            log.error("Error processing Tekra parameter {} for category {}: {}",
                                    paramEntry.getKey(), category.getNameBg(), e.getMessage());
                        }
                    }

                    totalCreated += categoryParamsCreated;
                    totalUpdated += categoryParamsUpdated;
                    totalErrors += categoryParamsErrors;
                    totalParameterOptionsCreated += categoryOptionsCreated;
                    totalParameterOptionsUpdated += categoryOptionsUpdated;

                    log.info("Category '{}' parameters sync completed - Parameters: {} created, {} updated. Options: {} created, {} updated. Errors: {}",
                            category.getNameBg(), categoryParamsCreated, categoryParamsUpdated,
                            categoryOptionsCreated, categoryOptionsUpdated, categoryParamsErrors);

                } catch (Exception e) {
                    totalErrors++;
                    log.error("Error syncing parameters for category '{}': {}",
                            category.getNameBg(), e.getMessage(), e);
                }
            }

            String message = String.format("Parameters: %d created, %d updated. Options: %d created, %d updated",
                    totalCreated, totalUpdated, totalParameterOptionsCreated, totalParameterOptionsUpdated);
            if (totalErrors > 0) {
                message += String.format(". %d errors occurred", totalErrors);
            }

            updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, totalProcessed, totalCreated, totalUpdated,
                    totalErrors, message, startTime);

            log.info("Tekra parameters synchronization completed - Processed: {}, Created: {}, Updated: {}, Errors: {}",
                    totalProcessed, totalCreated, totalUpdated, totalErrors);
            log.info("Parameter options - Created: {}, Updated: {}",
                    totalParameterOptionsCreated, totalParameterOptionsUpdated);

        } catch (Exception e) {
            updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, e.getMessage(), startTime);
            log.error("Error during Tekra parameters synchronization", e);
            throw e;
        }
    }

    @Transactional
    public void syncTekraProducts() {
        SyncLog syncLog = createSyncLogSimple("TEKRA_PRODUCTS");
        long startTime = System.currentTimeMillis();

        try {
            log.info("=== STARTING Tekra products synchronization with categoryPath matching ===");

            fixDuplicateProducts();

            // ✅ НОВО: Анализираме категориите преди да започнем
            analyzeCategoryPaths();

            // STEP 1: Fetch products from all categories
            log.info("STEP 1: Fetching products from all Tekra categories...");
            List<Map<String, Object>> allProducts = new ArrayList<>();
            Set<String> processedSkus = new HashSet<>();

            List<Category> allCategories = categoryRepository.findAll().stream()
                    .filter(cat -> cat.getTekraSlug() != null && !cat.getTekraSlug().isEmpty())
                    .toList();

            log.info("Found {} categories with Tekra slugs", allCategories.size());

            for (Category category : allCategories) {
                try {
                    String categorySlug = category.getTekraSlug();
                    log.info("Fetching products for category: {} (path: {})",
                            category.getNameBg(), category.getCategoryPath());

                    List<Map<String, Object>> categoryProducts = tekraApiService.getProductsRaw(categorySlug);
                    log.info("Found {} products in category '{}'", categoryProducts.size(), category.getNameBg());

                    for (Map<String, Object> product : categoryProducts) {
                        String sku = getStringValue(product, "sku");
                        if (sku != null && !processedSkus.contains(sku)) {
                            allProducts.add(product);
                            processedSkus.add(sku);
                        }
                    }

                } catch (Exception e) {
                    log.error("Error fetching products for category '{}': {}",
                            category.getNameBg(), e.getMessage());
                }
            }

            log.info("STEP 1 COMPLETE: Collected {} unique products from {} categories",
                    allProducts.size(), allCategories.size());

            if (allProducts.isEmpty()) {
                log.warn("No products found in any category");
                updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, 0, 0, 0, 0, "No products found", startTime);
                return;
            }

            // STEP 2: Prepare category maps (including categoryPath)
            log.info("STEP 2: Loading categories with paths for matching...");
            Map<String, Category> categoriesByPath = new HashMap<>();
            Map<String, Category> categoriesByName = new HashMap<>();
            Map<String, Category> categoriesBySlug = new HashMap<>();
            Map<String, Category> categoriesByTekraSlug = new HashMap<>();

            for (Category cat : allCategories) {
                // ✅ НОВО: Индексираме по categoryPath
                if (cat.getCategoryPath() != null) {
                    categoriesByPath.put(cat.getCategoryPath().toLowerCase(), cat);
                    log.debug("Indexed category by path: '{}' -> '{}'",
                            cat.getCategoryPath(), cat.getNameBg());
                }

                if (cat.getNameBg() != null) {
                    categoriesByName.put(cat.getNameBg().toLowerCase(), cat);
                }

                if (cat.getSlug() != null) {
                    categoriesBySlug.put(cat.getSlug().toLowerCase(), cat);
                }

                if (cat.getTekraSlug() != null) {
                    categoriesByTekraSlug.put(cat.getTekraSlug().toLowerCase(), cat);
                }
            }

            log.info("Category maps created: byPath={}, byName={}, bySlug={}, byTekraSlug={}",
                    categoriesByPath.size(), categoriesByName.size(),
                    categoriesBySlug.size(), categoriesByTekraSlug.size());

            // STEP 3: Process products with improved matching
            log.info("STEP 3: Processing {} products with categoryPath matching...", allProducts.size());

            long totalProcessed = 0, totalCreated = 0, totalUpdated = 0, totalErrors = 0;
            long skippedNoCategory = 0;

            // ✅ НОВО: Статистика за мачване
            Map<String, Integer> matchTypeStats = new HashMap<>();
            matchTypeStats.put("perfect_path", 0);
            matchTypeStats.put("partial_path", 0);
            matchTypeStats.put("name_match", 0);
            matchTypeStats.put("no_match", 0);

            for (int i = 0; i < allProducts.size(); i++) {
                Map<String, Object> rawProduct = allProducts.get(i);

                try {
                    String sku = getStringValue(rawProduct, "sku");
                    String name = getStringValue(rawProduct, "name");

                    if (sku == null || name == null) {
                        log.debug("Skipping product with missing SKU or name");
                        totalErrors++;
                        continue;
                    }

                    // ✅ НОВО: Използваме подобреното мачване с categoryPath
                    Category productCategory = findMostSpecificCategory(rawProduct,
                            categoriesByName, categoriesBySlug, categoriesByTekraSlug);

                    if (productCategory != null && !isValidCategory(productCategory)) {
                        log.warn("Invalid category found for product {}, rejecting", sku);
                        productCategory = null;
                    }

                    if (productCategory == null) {
                        log.warn("✗✗✗ Skipping product '{}' ({}): NO CATEGORY MAPPING", name, sku);
                        skippedNoCategory++;
                        matchTypeStats.put("no_match", matchTypeStats.get("no_match") + 1);
                        continue;
                    }

                    log.info("✓✓✓ Product '{}' → category: '{}' (path: '{}')",
                            sku, productCategory.getNameBg(), productCategory.getCategoryPath());

                    Product product = findOrCreateProduct(sku, rawProduct, productCategory);

                    if (product.getId() == null) {
                        totalCreated++;
                    } else {
                        totalUpdated++;
                    }

                    product.setCategory(productCategory);
                    product = productRepository.save(product);

                    setTekraParametersToProduct(product, rawProduct);
                    product = productRepository.save(product);

                    totalProcessed++;

                    if (totalProcessed % 20 == 0) {
                        log.info("Progress: {}/{} (created: {}, updated: {}, errors: {}, skipped: {})",
                                totalProcessed, allProducts.size(), totalCreated, totalUpdated,
                                totalErrors, skippedNoCategory);
                    }

                    if (totalProcessed % 50 == 0) {
                        entityManager.flush();
                        entityManager.clear();
                    }

                } catch (Exception e) {
                    totalErrors++;
                    log.error("Error processing product {}: {}",
                            getStringValue(rawProduct, "sku"), e.getMessage(), e);
                }
            }

            // ✅ НОВО: Показваме статистика за мачването
            log.info("=== CATEGORY MATCHING STATISTICS ===");
            matchTypeStats.forEach((type, count) ->
                    log.info("{}: {}", type, count)
            );
            log.info("====================================");

            String message = String.format(
                    "Total: %d, Created: %d, Updated: %d, Skipped (No Category): %d, Errors: %d",
                    totalProcessed, totalCreated, totalUpdated, skippedNoCategory, totalErrors
            );

            updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, totalProcessed, totalCreated,
                    totalUpdated, totalErrors, message, startTime);

            log.info("=== COMPLETE: Products sync finished in {}ms ===",
                    System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, e.getMessage(), startTime);
            log.error("=== FAILED: Products synchronization error ===", e);
            throw e;
        }
    }

    // ============ HELPER METHODS FOR STRUCTURAL CATEGORIES ============

    /**
     * ✅ FIXED: Създава/обновява Tekra категория с правилен slug генериране
     */
    private Category createOrUpdateTekraCategory(Map<String, Object> rawData,
                                                 Map<String, Category> existingCategories,
                                                 Category parentCategory) {
        try {
            String tekraId = getString(rawData, "id");
            String tekraSlug = getString(rawData, "slug");
            String name = getString(rawData, "name");

            if (tekraId == null || tekraSlug == null || name == null) {
                log.warn("Cannot create category with missing fields: id={}, slug={}, name={}",
                        tekraId, tekraSlug, name);
                return null;
            }

            // ✅ КРИТИЧНО: Търсим съществуваща категория ПО tekra_id И parent
            Optional<Category> existingCategoryOpt = findExistingCategoryByTekraData(
                    tekraId, tekraSlug, parentCategory);

            Category category;
            boolean isNew = false;

            if (existingCategoryOpt.isPresent()) {
                category = existingCategoryOpt.get();

                // ✅ ВАЖНА ВАЛИДАЦИЯ: Проверяваме дали parent-ът съвпада!
                boolean parentMatches = false;
                if (parentCategory == null && category.getParent() == null) {
                    parentMatches = true;
                } else if (parentCategory != null && category.getParent() != null &&
                        parentCategory.getId().equals(category.getParent().getId())) {
                    parentMatches = true;
                }

                if (!parentMatches) {
                    // Parent-ите не съвпадат - създаваме НОВА категория!
                    log.warn("Found category '{}' (ID:{}) with tekra_id={} but WRONG parent! Expected parent_id={}, found parent_id={}. Creating NEW category.",
                            name, category.getId(), tekraId,
                            parentCategory != null ? parentCategory.getId() : "NULL",
                            category.getParent() != null ? category.getParent().getId() : "NULL");

                    category = new Category();
                    category.setTekraId(tekraId);
                    isNew = true;
                } else {
                    log.debug("Found existing category with matching parent: '{}' (ID: {}, tekra_id: {})",
                            name, category.getId(), tekraId);
                }
            } else {
                category = new Category();
                category.setTekraId(tekraId);
                isNew = true;
                log.info("Creating NEW category: '{}' (tekra_id: {})", name, tekraId);
            }

            // ✅ Задаваме полетата
            category.setTekraSlug(tekraSlug);
            category.setNameBg(name);
            category.setNameEn(name);
            category.setParent(parentCategory);

            // Parse count и show flag
            String countStr = getString(rawData, "count");
            if (countStr != null && !countStr.isEmpty()) {
                try {
                    Integer count = Integer.parseInt(countStr);
                    category.setSortOrder(count);
                    category.setShow(count > 0);
                } catch (NumberFormatException e) {
                    category.setSortOrder(0);
                    category.setShow(true);
                }
            } else {
                category.setShow(true);
                category.setSortOrder(0);
            }

            // ✅ КРИТИЧНО: Генерираме slug ПРЕДИ да запазим
            String uniqueSlug = generateUniqueSlug(tekraSlug, name, parentCategory, existingCategories);
            category.setSlug(uniqueSlug);

            // ✅ Генерираме categoryPath
            category.setCategoryPath(category.generateCategoryPath());

            // ✅ ВАЖНО: Запазваме в БД за да получим ID
            category = categoryRepository.save(category);

            // ✅ Flush за да гарантираме че е в БД
            categoryRepository.flush();

            String parentInfo = parentCategory != null ?
                    String.format("parent='%s' (ID:%d)", parentCategory.getNameBg(), parentCategory.getId()) :
                    "ROOT";

            if (isNew) {
                log.info("✓ CREATED: '{}' | slug='{}' | path='{}' | {} | ID={}",
                        name, uniqueSlug, category.getCategoryPath(), parentInfo, category.getId());
            } else {
                log.info("✓ UPDATED: '{}' | slug='{}' | path='{}' | {} | ID={}",
                        name, uniqueSlug, category.getCategoryPath(), parentInfo, category.getId());
            }

            return category;

        } catch (Exception e) {
            log.error("Error creating/updating category from Tekra: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create/update category: " + getString(rawData, "name"), e);
        }
    }

    /**
     * ✅ FIXED: Генерира уникален slug КОНСТАНТНО за същата категория
     * <p>
     * Правила:
     * - Level-1 (root): samo tekraSlug (напр. "videonablyudenie")
     * - Level-2: parent-slug + tekraSlug (напр. "videonablyudenie-ip-sistemi")
     * - Level-3: parent-slug + tekraSlug (напр. "videonablyudenie-ip-sistemi-kameri")
     * <p>
     * Ако има конфликт, добавя tekra_id като дискриминатор
     */
    private String generateUniqueSlug(String tekraSlug, String categoryName,
                                      Category parentCategory,
                                      Map<String, Category> existingCategories) {
        if (tekraSlug == null || tekraSlug.isEmpty()) {
            log.warn("Tekra slug is null/empty for category: {}", categoryName);
            tekraSlug = createSlugFromName(categoryName);
        }

        String baseSlug = tekraSlug;

        // ✅ ПРАВИЛО 1: Root категории (без parent)
        if (parentCategory == null) {
            if (!slugExistsInMap(baseSlug, existingCategories) &&
                    !slugExistsInDatabase(baseSlug, null)) {
                log.debug("Root slug: '{}'", baseSlug);
                return baseSlug;
            }

            // Ако има конфликт, добави "-root"
            String rootSlug = baseSlug + "-root";
            log.debug("Root slug with suffix: '{}'", rootSlug);
            return rootSlug;
        }

        // ✅ ПРАВИЛО 2: Категории с parent
        String parentSlug = parentCategory.getSlug();
        if (parentSlug == null || parentSlug.isEmpty()) {
            log.warn("Parent category '{}' has no slug!", parentCategory.getNameBg());
            parentSlug = parentCategory.getTekraSlug();
            if (parentSlug == null) {
                parentSlug = "cat-" + parentCategory.getId();
            }
        }

        // Комбиниран slug: parent + current
        String hierarchicalSlug = parentSlug + "-" + baseSlug;

        log.debug("Generating slug for '{}': parent='{}', tekra='{}', combined='{}'",
                categoryName, parentSlug, baseSlug, hierarchicalSlug);

        // Проверка за конфликт
        if (!slugExistsInMap(hierarchicalSlug, existingCategories) &&
                !slugExistsInDatabase(hierarchicalSlug, parentCategory)) {
            log.debug("✓ Hierarchical slug OK: '{}'", hierarchicalSlug);
            return hierarchicalSlug;
        }

        // ✅ ПРАВИЛО 3: Ако има конфликт, провери дали конфликтът е със СЪЩАТА категория
        Category existing = existingCategories.get(hierarchicalSlug);
        if (existing != null) {
            // Проверка дали parent-ът е същият
            if (existing.getParent() != null && parentCategory != null &&
                    existing.getParent().getId().equals(parentCategory.getId())) {
                // Същата категория, използваме същия slug
                log.debug("✓ Reusing existing slug: '{}'", hierarchicalSlug);
                return hierarchicalSlug;
            }
        }

        // ✅ ПРАВИЛО 4: Реален конфликт - добави discriminator
        // Използваме името като discriminator вместо число
        String discriminator = extractDiscriminator(categoryName);
        if (discriminator != null && !discriminator.isEmpty()) {
            String discriminatedSlug = hierarchicalSlug + "-" + discriminator;
            if (!slugExistsInMap(discriminatedSlug, existingCategories) &&
                    !slugExistsInDatabase(discriminatedSlug, parentCategory)) {
                log.debug("✓ Discriminated slug: '{}'", discriminatedSlug);
                return discriminatedSlug;
            }
        }

        // ✅ ПРАВИЛО 5: Последен fallback - числов суфикс
        int counter = 2; // Започваме от 2 (1 е оригиналния)
        String numberedSlug;
        do {
            numberedSlug = hierarchicalSlug + "-" + counter;
            counter++;
        } while ((slugExistsInMap(numberedSlug, existingCategories) ||
                slugExistsInDatabase(numberedSlug, parentCategory)) && counter < 100);

        log.warn("Had to use numbered slug for '{}': '{}'", categoryName, numberedSlug);
        return numberedSlug;
    }

    /**
     * ✅ UPDATED: Проверява дали slug съществува в map
     */
    private boolean slugExistsInMap(String slug, Map<String, Category> existingCategories) {
        return existingCategories.containsKey(slug);
    }

    /**
     * ✅ UPDATED: Проверява дали slug съществува в БД
     */
    private boolean slugExistsInDatabase(String slug, Category parentCategory) {
        List<Category> existing = categoryRepository.findAll().stream()
                .filter(cat -> slug.equals(cat.getSlug()))
                .toList();

        if (existing.isEmpty()) {
            return false;
        }

        // Ако имаме parent constraint, проверяваме само за различен parent
        if (parentCategory != null) {
            for (Category cat : existing) {
                Category catParent = cat.getParent();

                // Конфликт само ако parent-ите са различни
                if (catParent == null && parentCategory != null) {
                    return true; // Категория без parent vs с parent
                }
                if (catParent != null && parentCategory == null) {
                    return true; // Категория с parent vs без parent
                }
                if (catParent != null && !catParent.getId().equals(parentCategory.getId())) {
                    return true; // Различни родители
                }
            }
            return false; // Същия parent, не е конфликт
        }

        return true; // Има категория с този slug
    }

    /**
     * ✅ SIMPLIFIED: Извлича кратък discriminator от името
     */
    private String extractDiscriminator(String categoryName) {
        if (categoryName == null || categoryName.isEmpty()) {
            return null;
        }

        String lowerName = categoryName.toLowerCase();

        // Кратки ключови думи които помагат за разпознаване
        Map<String, String> keywords = Map.ofEntries(
                Map.entry("ip", "ip"),
                Map.entry("аналогов", "analog"),
                Map.entry("hd", "hd"),
                Map.entry("wifi", "wifi"),
                Map.entry("безжичн", "wireless"),
                Map.entry("куполн", "dome"),
                Map.entry("булет", "bullet"),
                Map.entry("вътрешн", "indoor"),
                Map.entry("външн", "outdoor"),
                Map.entry("nvr", "nvr"),
                Map.entry("dvr", "dvr")
        );

        // Търси ключова дума в името
        for (Map.Entry<String, String> entry : keywords.entrySet()) {
            if (lowerName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Ако няма ключова дума, използваме първата значима дума
        String[] words = lowerName.split("\\s+");
        if (words.length > 0 && words[0].length() > 2) {
            String transliterated = transliterateCyrillic(words[0]);
            return transliterated
                    .toLowerCase()
                    .replaceAll("[^a-z0-9]", "")
                    .substring(0, Math.min(4, transliterated.length()));
        }

        return null;
    }

    /**
     * ✅ HELPER: Намира съществуваща категория по Tekra данни
     */
    private Optional<Category> findExistingCategoryByTekraData(String tekraId,
                                                               String tekraSlug,
                                                               Category parentCategory) {
        // Първо опит по tekra_id (най-точно)
        if (tekraId != null) {
            List<Category> byTekraId = categoryRepository.findAll().stream()
                    .filter(cat -> tekraId.equals(cat.getTekraId()))
                    .toList();

            if (!byTekraId.isEmpty()) {
                // ✅ КРИТИЧНО: Филтрираме по parent!
                for (Category cat : byTekraId) {
                    boolean parentMatches = false;

                    if (parentCategory == null && cat.getParent() == null) {
                        // И двете са root
                        parentMatches = true;
                    } else if (parentCategory != null && cat.getParent() != null &&
                            parentCategory.getId().equals(cat.getParent().getId())) {
                        // Същият parent
                        parentMatches = true;
                    }

                    if (parentMatches) {
                        log.debug("Found by tekra_id with matching parent: '{}' (ID:{}, parent_id:{})",
                                cat.getNameBg(), cat.getId(),
                                cat.getParent() != null ? cat.getParent().getId() : "NULL");
                        return Optional.of(cat);
                    } else {
                        log.debug("Found by tekra_id but WRONG parent: '{}' (ID:{}, expected parent_id:{}, found parent_id:{})",
                                cat.getNameBg(), cat.getId(),
                                parentCategory != null ? parentCategory.getId() : "NULL",
                                cat.getParent() != null ? cat.getParent().getId() : "NULL");
                    }
                }

                // Има категория с този tekra_id но с РАЗЛИЧЕН parent
                log.debug("Category with tekra_id={} exists but with different parent", tekraId);
                return Optional.empty();
            }
        }

        // Втори опит по tekra_slug (по-неточно, може да има дубликати)
        if (tekraSlug != null) {
            List<Category> byTekraSlug = categoryRepository.findAll().stream()
                    .filter(cat -> tekraSlug.equals(cat.getTekraSlug()))
                    .toList();

            if (!byTekraSlug.isEmpty()) {
                // ✅ Филтрираме по parent
                for (Category cat : byTekraSlug) {
                    boolean parentMatches = false;

                    if (parentCategory == null && cat.getParent() == null) {
                        parentMatches = true;
                    } else if (parentCategory != null && cat.getParent() != null &&
                            parentCategory.getId().equals(cat.getParent().getId())) {
                        parentMatches = true;
                    }

                    if (parentMatches) {
                        log.debug("Found by tekra_slug with matching parent: '{}' (ID:{})",
                                cat.getNameBg(), cat.getId());
                        return Optional.of(cat);
                    }
                }

                log.debug("Category with tekra_slug={} exists but with different parent", tekraSlug);
                return Optional.empty();
            }
        }

        log.debug("No existing category found for tekra_id={}, tekra_slug={}, parent_id={}",
                tekraId, tekraSlug, parentCategory != null ? parentCategory.getId() : "NULL");
        return Optional.empty();
    }

    // ============ PRODUCT CATEGORY MAPPING ============

    private Category findMostSpecificCategory(Map<String, Object> product,
                                              Map<String, Category> categoriesByName,
                                              Map<String, Category> categoriesBySlug,
                                              Map<String, Category> categoriesByTekraSlug) {
        String category3 = getStringValue(product, "category_3");
        String category2 = getStringValue(product, "category_2");
        String category1 = getStringValue(product, "category_1");

        log.debug("Matching product to category: L1='{}', L2='{}', L3='{}'",
                category1, category2, category3);

        // ✅ НОВО: Създаваме пълен път от XML категориите
        String expectedPath = buildCategoryPath(category1, category2, category3);

        if (expectedPath != null) {
            log.debug("Expected category path: '{}'", expectedPath);

            // Търсим категория с точно този път
            Optional<Category> exactMatch = categoryRepository.findAll().stream()
                    .filter(cat -> expectedPath.equalsIgnoreCase(cat.getCategoryPath()))
                    .findFirst();

            if (exactMatch.isPresent() && isValidCategory(exactMatch.get())) {
                log.info("✓✓✓ PERFECT PATH MATCH: '{}' -> '{}'",
                        expectedPath, exactMatch.get().getNameBg());
                return exactMatch.get();
            }
        }

        // Fallback: Опит по части на пътя (отзад напред за най-специфична категория)
        if (category3 != null && category2 != null) {
            String partialPath = buildCategoryPath(null, category2, category3);
            Optional<Category> match = findCategoryByPartialPath(partialPath);
            if (match.isPresent() && isValidCategory(match.get())) {
                log.info("✓✓ PARTIAL PATH MATCH (L2+L3): '{}' -> '{}'",
                        partialPath, match.get().getNameBg());
                return match.get();
            }
        }

        if (category3 != null) {
            Optional<Category> match = findCategoryByName(category3, category2);
            if (match.isPresent() && isValidCategory(match.get())) {
                log.info("✓ NAME MATCH (L3): '{}' -> '{}'",
                        category3, match.get().getNameBg());
                return match.get();
            }
        }

        if (category2 != null) {
            Optional<Category> match = findCategoryByName(category2, category1);
            if (match.isPresent() && isValidCategory(match.get())) {
                log.info("✓ NAME MATCH (L2): '{}' -> '{}'",
                        category2, match.get().getNameBg());
                return match.get();
            }
        }

        log.warn("✗✗✗ NO MATCH for path: '{}'", expectedPath);
        return null;
    }

    private String buildCategoryPath(String category1, String category2, String category3) {
        List<String> parts = new ArrayList<>();

        if (category1 != null) {
            parts.add(normalizeCategoryForPath(category1));
        }
        if (category2 != null) {
            parts.add(normalizeCategoryForPath(category2));
        }
        if (category3 != null) {
            parts.add(normalizeCategoryForPath(category3));
        }

        return parts.isEmpty() ? null : String.join("/", parts);
    }

    /**
     * НОВО: Нормализира име на категория за използване в път
     */
    private String normalizeCategoryForPath(String categoryName) {
        if (categoryName == null) return null;

        // Това е важно - трябва да съвпада с логиката в createSlugFromName()
        String transliterated = transliterateCyrillic(categoryName.trim());

        return transliterated.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    /**
     * НОВО: Търси категория по частичен път (suffix matching)
     */
    private Optional<Category> findCategoryByPartialPath(String partialPath) {
        if (partialPath == null) return Optional.empty();

        return categoryRepository.findAll().stream()
                .filter(cat -> cat.getCategoryPath() != null)
                .filter(cat -> cat.getCategoryPath().toLowerCase().endsWith(partialPath.toLowerCase()))
                .findFirst();
    }

    /**
     * НОВО: Подобрено търсене по име с валидация на parent
     */
    private Optional<Category> findCategoryByName(String categoryName, String expectedParentName) {
        if (categoryName == null) return Optional.empty();

        List<Category> candidates = categoryRepository.findAll().stream()
                .filter(cat -> categoryName.equalsIgnoreCase(cat.getNameBg()))
                .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // Ако имаме очаквано име на parent, филтрираме
        if (expectedParentName != null) {
            for (Category candidate : candidates) {
                if (candidate.getParent() != null &&
                        expectedParentName.equalsIgnoreCase(candidate.getParent().getNameBg())) {
                    return Optional.of(candidate);
                }
            }
        }

        // Връщаме първия валиден кандидат
        return candidates.stream().findFirst();
    }

    private Category findCategoryByNameEnhanced(String categoryName,
                                                Map<String, Category> categoriesByName,
                                                Map<String, Category> categoriesBySlug) {
        if (categoryName == null) return null;

        String searchName = categoryName.trim().toLowerCase();

        Category exactMatch = categoriesByName.get(searchName);
        if (exactMatch != null) return exactMatch;

        for (String availableName : categoriesByName.keySet()) {
            if (availableName.equals(searchName) ||
                    availableName.contains(searchName) ||
                    searchName.contains(availableName)) {
                return categoriesByName.get(availableName);
            }
        }

        String slug = createSlugFromName(categoryName);
        return categoriesBySlug.get(slug.toLowerCase());
    }

    // ============ HELPER METHODS ============

    private void syncValiParameterOptions(Parameter parameter, List<ParameterOptionRequestDto> externalOptions) {
        if (externalOptions == null || externalOptions.isEmpty()) {
            log.debug("No options provided for parameter: {}", parameter.getNameBg());
            return;
        }

        Map<Long, ParameterOption> existingOptions = parameterOptionRepository
                .findByParameterIdOrderByOrderAsc(parameter.getId())
                .stream()
                .filter(option -> option.getExternalId() != null)
                .collect(Collectors.toMap(ParameterOption::getExternalId, option -> option));

        List<ParameterOption> optionsToSave = new ArrayList<>();
        int created = 0, updated = 0;

        for (ParameterOptionRequestDto extOption : externalOptions) {
            ParameterOption option = existingOptions.get(extOption.getId());

            if (option == null) {
                option = createValiParameterOptionFromExternal(extOption, parameter);
                if (option != null) {
                    created++;
                }
            } else {
                updateValiParameterOptionFromExternal(option, extOption);
                updated++;
            }

            if (option != null) {
                optionsToSave.add(option);
            }
        }

        if (!optionsToSave.isEmpty()) {
            parameterOptionRepository.saveAll(optionsToSave);
            log.debug("Saved {} options for parameter: {} (created: {}, updated: {})",
                    optionsToSave.size(), parameter.getNameBg(), created, updated);
        }
    }

    private ParameterOption createValiParameterOptionFromExternal(ParameterOptionRequestDto extOption, Parameter parameter) {
        try {
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
        } catch (Exception e) {
            log.error("Error creating Vali parameter option from external data: {}", e.getMessage());
            return null;
        }
    }

    private void updateValiParameterOptionFromExternal(ParameterOption option, ParameterOptionRequestDto extOption) {
        try {
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
        } catch (Exception e) {
            log.error("Error updating Vali parameter option: {}", e.getMessage());
        }
    }

    private void setParametersToProduct(Product product, ProductRequestDto extProduct) {
        if (extProduct.getParameters() == null || product.getCategory() == null) {
            product.setProductParameters(new HashSet<>());
            return;
        }

        Set<ProductParameter> newProductParameters = new HashSet<>();
        int mappedCount = 0;
        int notFoundCount = 0;

        for (ParameterValueRequestDto paramValue : extProduct.getParameters()) {
            try {
                Optional<Parameter> parameterOpt = cachedLookupService
                        .getParameter(paramValue.getParameterId(), product.getCategory().getId());

                if (parameterOpt.isEmpty()) {
                    parameterOpt = parameterRepository.findByExternalIdAndCategoryId(
                            paramValue.getParameterId(), product.getCategory().getId());

                    if (parameterOpt.isEmpty()) {
                        log.debug("Parameter not found: parameterId={}, categoryId={}, productId={}",
                                paramValue.getParameterId(), product.getCategory().getId(), product.getExternalId());
                        notFoundCount++;
                        continue;
                    }
                }

                Parameter parameter = parameterOpt.get();

                Optional<ParameterOption> optionOpt = cachedLookupService
                        .getParameterOption(paramValue.getOptionId(), parameter.getId());

                if (optionOpt.isEmpty()) {
                    optionOpt = parameterOptionRepository
                            .findByParameterIdOrderByOrderAsc(parameter.getId())
                            .stream()
                            .filter(opt -> paramValue.getOptionId().equals(opt.getExternalId()))
                            .findFirst();

                    if (optionOpt.isEmpty()) {
                        log.debug("Parameter option not found: optionId={}, parameterId={}, productId={}",
                                paramValue.getOptionId(), parameter.getId(), product.getExternalId());
                        notFoundCount++;
                        continue;
                    }
                }

                ParameterOption option = optionOpt.get();

                ProductParameter pp = new ProductParameter();
                pp.setProduct(product);
                pp.setParameter(parameter);
                pp.setParameterOption(option);
                newProductParameters.add(pp);

                mappedCount++;

            } catch (Exception e) {
                log.error("Error mapping parameter for product {}: {}",
                        product.getExternalId(), e.getMessage());
                notFoundCount++;
            }
        }

        product.setProductParameters(newProductParameters);

        if (mappedCount > 0 || notFoundCount > 0) {
            log.debug("Product {} parameter mapping: {} mapped, {} not found",
                    product.getExternalId(), mappedCount, notFoundCount);
        }
    }

    private void setTekraParametersToProduct(Product product, Map<String, Object> rawProduct) {
        try {
            if (product.getCategory() == null) {
                log.warn("Product {} has no category, cannot set parameters", product.getSku());
                return;
            }

            Set<ProductParameter> productParameters = new HashSet<>();
            int mappedCount = 0;
            int notFoundCount = 0;

            Map<String, String> parameterMappings = extractTekraParameters(rawProduct);

            for (Map.Entry<String, String> paramEntry : parameterMappings.entrySet()) {
                try {
                    String parameterKey = paramEntry.getKey();
                    String parameterValue = paramEntry.getValue();

                    Optional<Parameter> parameterOpt = parameterRepository
                            .findByTekraKeyAndCategoryId(parameterKey, product.getCategory().getId());

                    if (parameterOpt.isEmpty()) {
                        String parameterName = convertTekraParameterKeyToName(parameterKey);
                        parameterOpt = parameterRepository.findByCategoryAndNameBg(product.getCategory(), parameterName);
                    }

                    if (parameterOpt.isEmpty()) {
                        log.debug("Parameter not found: key={}, categoryId={}, productSku={}",
                                parameterKey, product.getCategory().getId(), product.getSku());
                        notFoundCount++;
                        continue;
                    }

                    Parameter parameter = parameterOpt.get();

                    ParameterOption option = findOrCreateParameterOption(parameter, parameterValue);
                    if (option == null) {
                        log.debug("Parameter option not found: parameter={}, value={}, productSku={}",
                                parameter.getNameBg(), parameterValue, product.getSku());
                        notFoundCount++;
                        continue;
                    }

                    ProductParameter productParam = new ProductParameter();
                    productParam.setProduct(product);
                    productParam.setParameter(parameter);
                    productParam.setParameterOption(option);
                    productParameters.add(productParam);

                    mappedCount++;
                    log.debug("Mapped parameter: {} = {} for product {}",
                            parameter.getNameBg(), parameterValue, product.getSku());

                } catch (Exception e) {
                    log.error("Error mapping parameter {} for product {}: {}",
                            paramEntry.getKey(), product.getSku(), e.getMessage());
                    notFoundCount++;
                }
            }

            product.setProductParameters(productParameters);

            if (mappedCount > 0 || notFoundCount > 0) {
                log.info("Product {} parameter mapping: {} mapped, {} not found",
                        product.getSku(), mappedCount, notFoundCount);
            }

        } catch (Exception e) {
            log.error("Error setting Tekra parameters for product {}: {}", product.getSku(), e.getMessage());
        }
    }

    private Map<String, String> extractTekraParameters(Map<String, Object> rawProduct) {
        Map<String, String> parameters = new HashMap<>();

        String[] possibleParameters = {
                "cvjat", "merna", "model", "rezolyutsiya", "ir_podsvetka",
                "razmer", "zvuk", "wdr", "obektiv", "korpus",
                "stepen_na_zashtita", "kompresiya", "poe_portove",
                "broy_izhodi", "raboten_tok", "moshtnost", "seriya_eco"
        };

        for (String paramKey : possibleParameters) {
            String value = getStringValue(rawProduct, paramKey);
            if (value != null && !value.trim().isEmpty()) {
                parameters.put(paramKey, value.trim());
            }
        }

        return parameters;
    }

    private ParameterOption findOrCreateParameterOption(Parameter parameter, String value) {
        try {
            Optional<ParameterOption> option = parameterOptionRepository.findByParameterAndNameBg(parameter, value);

            if (option.isPresent()) {
                return option.get();
            }

            List<ParameterOption> allOptions = parameterOptionRepository.findByParameterIdOrderByOrderAsc(parameter.getId());

            for (ParameterOption opt : allOptions) {
                if (value.equalsIgnoreCase(opt.getNameBg()) ||
                        (opt.getNameEn() != null && value.equalsIgnoreCase(opt.getNameEn()))) {
                    return opt;
                }
            }

            ParameterOption newOption = new ParameterOption();
            newOption.setParameter(parameter);
            newOption.setNameBg(value);
            newOption.setNameEn(value);
            newOption.setOrder(allOptions.size());

            newOption = parameterOptionRepository.save(newOption);
            log.debug("Created new parameter option: {} = {} for parameter {}",
                    parameter.getNameBg(), value, parameter.getNameBg());

            return newOption;

        } catch (Exception e) {
            log.error("Error finding/creating parameter option for {} = {}: {}",
                    parameter.getNameBg(), value, e.getMessage());
            return null;
        }
    }

    private void updateProductFieldsFromTekraXML(Product product, Map<String, Object> rawData, String categorySlug) {
        try {
            product.setReferenceNumber(getStringValue(rawData, "sku"));

            String name = getStringValue(rawData, "name");
            product.setNameBg(name);
            product.setNameEn(name);

            product.setModel(getStringValue(rawData, "model"));

            Double price = getDoubleValue(rawData, "price");
            if (price != null) {
                product.setPriceClient(BigDecimal.valueOf(price));
            }

            Double partnerPrice = getDoubleValue(rawData, "partner_price");
            if (partnerPrice != null) {
                product.setPricePartner(BigDecimal.valueOf(partnerPrice));
            }

            Integer quantity = getIntegerValue(rawData, "quantity");
            boolean inStock = (quantity != null && quantity > 0);
            product.setShow(inStock);
            product.setStatus(inStock ? ProductStatus.AVAILABLE : ProductStatus.NOT_AVAILABLE);

            String description = getStringValue(rawData, "description");
            if (description != null) {
                product.setDescriptionBg(description);
                product.setDescriptionEn(description);
            }

            Double weight = getDoubleValue(rawData, "weight");
            if (weight == null) {
                weight = getDoubleValue(rawData, "net_weight");
            }
            if (weight != null && weight > 0) {
                product.setWeight(BigDecimal.valueOf(weight));
            }

            setImagesFromTekraXML(product, rawData);

            // ✅ ВАЖНО: Ако няма категория, опитваме се да я намерим
            if (product.getCategory() == null) {
                setCategoryFromTekraXML(product, rawData, categorySlug);
            }

            String manufacturer = getStringValue(rawData, "manufacturer");
            if (manufacturer != null) {
                setManufacturerFromName(product, manufacturer);
            }

            product.calculateFinalPrice();

        } catch (Exception e) {
            log.error("Error updating product fields from Tekra XML: {}", e.getMessage());
            throw new RuntimeException("Failed to update product fields", e);
        }
    }

    private void setImagesFromTekraXML(Product product, Map<String, Object> rawData) {
        List<String> allImages = new ArrayList<>();

        String primaryImage = getStringValue(rawData, "image");
        if (primaryImage != null && !primaryImage.isEmpty()) {
            allImages.add(primaryImage);
        }

        Object galleryObj = rawData.get("gallery");
        if (galleryObj instanceof List) {
            List<?> galleryList = (List<?>) galleryObj;
            for (Object imageObj : galleryList) {
                if (imageObj instanceof String) {
                    String imageUrl = (String) imageObj;
                    if (!allImages.contains(imageUrl)) {
                        allImages.add(imageUrl);
                    }
                }
            }
        }

        if (!allImages.isEmpty()) {
            product.setPrimaryImageUrl(allImages.get(0));

            if (allImages.size() > 1) {
                List<String> additionalImages = allImages.subList(1, allImages.size());
                if (product.getAdditionalImages() != null) {
                    product.getAdditionalImages().clear();
                    product.getAdditionalImages().addAll(additionalImages);
                } else {
                    product.setAdditionalImages(new ArrayList<>(additionalImages));
                }
            }
        }
    }

    private void setCategoryFromTekraXML(Product product, Map<String, Object> rawData, String categorySlug) {
        try {
            String category1 = getStringValue(rawData, "category_1");
            String category2 = getStringValue(rawData, "category_2");
            String category3 = getStringValue(rawData, "category_3");

            log.debug("Setting category for product. Available categories: L1='{}', L2='{}', L3='{}', provided slug='{}'",
                    category1, category2, category3, categorySlug);

            if (categorySlug != null) {
                Optional<Category> categoryBySlug = categoryRepository.findByTekraSlug(categorySlug);
                if (categoryBySlug.isPresent()) {
                    product.setCategory(categoryBySlug.get());
                    log.debug("Set category from provided slug: {} -> {}", categorySlug, categoryBySlug.get().getNameBg());
                    return;
                } else {
                    log.warn("Category with slug '{}' not found in database", categorySlug);
                }
            }

            if (category3 != null) {
                categoryRepository.findByNameBg(category3)
                        .or(() -> categoryRepository.findByNameEn(category3))
                        .ifPresent(category -> {
                            product.setCategory(category);
                            log.debug("Set category from L3: {} -> {}", category3, category.getNameBg());
                        });
            } else if (category2 != null) {
                categoryRepository.findByNameBg(category2)
                        .or(() -> categoryRepository.findByNameEn(category2))
                        .ifPresent(category -> {
                            product.setCategory(category);
                            log.debug("Set category from L2: {} -> {}", category2, category.getNameBg());
                        });
            } else if (category1 != null) {
                categoryRepository.findByNameBg(category1)
                        .or(() -> categoryRepository.findByNameEn(category1))
                        .ifPresent(category -> {
                            product.setCategory(category);
                            log.debug("Set category from L1: {} -> {}", category1, category.getNameBg());
                        });
            }

            if (product.getCategory() == null) {
                log.warn("Could not set category for product. Available: L1='{}', L2='{}', L3='{}', slug='{}'",
                        category1, category2, category3, categorySlug);
            }

        } catch (Exception e) {
            log.error("Error setting category from Tekra XML for product: {}", e.getMessage());
        }
    }

    private void setManufacturerFromName(Product product, String manufacturerName) {
        manufacturerRepository.findByName(manufacturerName)
                .or(() -> {
                    Manufacturer manufacturer = new Manufacturer();
                    manufacturer.setName(manufacturerName);
                    return Optional.of(manufacturerRepository.save(manufacturer));
                })
                .ifPresent(product::setManufacturer);
    }

    private Product findOrCreateProduct(String sku, Map<String, Object> rawProduct, Category category) {
        try {
            List<Product> existing = productRepository.findProductsBySkuCode(sku);
            Product product;

            if (!existing.isEmpty()) {
                product = existing.get(0);
                if (existing.size() > 1) {
                    log.warn("Found {} duplicates for SKU: {}, keeping first", existing.size(), sku);
                    for (int i = 1; i < existing.size(); i++) {
                        productRepository.delete(existing.get(i));
                    }
                }
                updateProductFieldsFromTekraXML(product, rawProduct, category.getTekraSlug());
            } else {
                product = new Product();
                product.setSku(sku);
                updateProductFieldsFromTekraXML(product, rawProduct, category.getTekraSlug());
            }

            setTekraParametersToProduct(product, rawProduct);

            return product;

        } catch (Exception e) {
            log.error("Error in findOrCreateProduct for SKU {}: {}", sku, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void fixDuplicateProducts() {
        log.info("Checking for duplicate products...");

        List<Object[]> duplicates = productRepository.findDuplicateProductsBySku();

        if (!duplicates.isEmpty()) {
            log.warn("Found {} duplicate products by SKU", duplicates.size());

            for (Object[] duplicate : duplicates) {
                String sku = (String) duplicate[0];
                Long count = (Long) duplicate[1];

                log.info("SKU '{}' has {} duplicates", sku, count);

                List<Product> products = productRepository.findProductsBySkuCode(sku);
                if (products.size() > 1) {
                    Product productToKeep = products.get(0);
                    for (int i = 1; i < products.size(); i++) {
                        Product productToDelete = products.get(i);
                        log.info("Deleting duplicate product with ID: {}", productToDelete.getId());
                        productRepository.delete(productToDelete);
                    }
                    log.info("Kept product with ID: {}", productToKeep.getId());
                }
            }
        }

        List<Object[]> duplicateExternalIds = productRepository.findDuplicateProductsByExternalId();
        if (!duplicateExternalIds.isEmpty()) {
            log.warn("Found {} duplicate products by external ID", duplicateExternalIds.size());

            for (Object[] duplicate : duplicateExternalIds) {
                Long externalId = (Long) duplicate[0];
                Long count = (Long) duplicate[1];

                log.info("External ID '{}' has {} duplicates", externalId, count);

                List<Product> products = productRepository.findProductsByExternalId(externalId);
                if (products.size() > 1) {
                    Product productToKeep = products.get(0);
                    for (int i = 1; i < products.size(); i++) {
                        Product productToDelete = products.get(i);
                        log.info("Deleting duplicate product with ID: {}", productToDelete.getId());
                        productRepository.delete(productToDelete);
                    }
                }
            }
        }
    }

    private void analyzeProductCategories(List<Map<String, Object>> products, Map<String, Category> categoriesByName) {
        log.info("=== PRODUCT CATEGORY ANALYSIS ===");

        Set<String> foundCategory1 = new HashSet<>();
        Set<String> foundCategory2 = new HashSet<>();
        Set<String> foundCategory3 = new HashSet<>();

        for (Map<String, Object> product : products) {
            String cat1 = getStringValue(product, "category_1");
            String cat2 = getStringValue(product, "category_2");
            String cat3 = getStringValue(product, "category_3");

            if (cat1 != null) foundCategory1.add(cat1);
            if (cat2 != null) foundCategory2.add(cat2);
            if (cat3 != null) foundCategory3.add(cat3);
        }

        log.info("Found in products - Category 1 ({}): {}", foundCategory1.size(), foundCategory1);
        log.info("Found in products - Category 2 ({}): {}", foundCategory2.size(), foundCategory2);
        log.info("Found in products - Category 3 ({}): {}", foundCategory3.size(), foundCategory3);

        log.info("Available categories ({}): {}", categoriesByName.size(),
                categoriesByName.keySet().stream().limit(20).collect(Collectors.toList()));

        checkCategoryMatches(foundCategory1, "Level 1", categoriesByName);
        checkCategoryMatches(foundCategory2, "Level 2", categoriesByName);
        checkCategoryMatches(foundCategory3, "Level 3", categoriesByName);

        log.info("=================================");
    }

    private void checkCategoryMatches(Set<String> foundCategories, String level, Map<String, Category> categoriesByName) {
        int matches = 0;
        List<String> unmatched = new ArrayList<>();

        for (String foundCat : foundCategories) {
            if (categoriesByName.containsKey(foundCat.toLowerCase())) {
                matches++;
            } else {
                unmatched.add(foundCat);
            }
        }

        log.info("{} categories: {}/{} matched, {} unmatched: {}",
                level, matches, foundCategories.size(), unmatched.size(), unmatched);
    }

    private boolean isValidCategory(Category category) {
        if (category == null) {
            return false;
        }

        if (category.getId() == null) {
            log.warn("Category has null ID");
            return false;
        }

        if (category.getNameBg() == null || category.getNameBg().trim().isEmpty()) {
            log.warn("Category has empty name");
            return false;
        }

        return true;
    }

    private String normalizeCategoryName(String name) {
        if (name == null) return null;

        return name.toLowerCase()
                .replace("системи", "")
                .replace("камери", "")
                .replace("устройства", "")
                .replace("аксесоари", "")
                .replace("видео", "")
                .replace("наблюдение", "")
                .replaceAll("[^a-zа-я0-9\\s]", "")
                .trim();
    }

    private String convertTekraParameterKeyToName(String parameterKey) {
        Map<String, String> parameterTranslations = Map.ofEntries(
                Map.entry("cvjat", "Цвят"),
                Map.entry("merna", "Мерна единица"),
                Map.entry("model", "Модел"),
                Map.entry("rezolyutsiya", "Резолюция"),
                Map.entry("ir_podsvetka", "IR подсветка"),
                Map.entry("razmer", "Размери"),
                Map.entry("zvuk", "Звук"),
                Map.entry("wdr", "WDR"),
                Map.entry("obektiv", "Обектив"),
                Map.entry("korpus", "Корпус"),
                Map.entry("stepen_na_zashtita", "Степен на защита"),
                Map.entry("kompresiya", "Компресия"),
                Map.entry("poe_portove", "PoE портове"),
                Map.entry("broy_izhodi", "Брой изходи"),
                Map.entry("raboten_tok", "Работен ток"),
                Map.entry("moshtnost", "Мощност"),
                Map.entry("seriya_eco", "Еco серия")
        );

        return parameterTranslations.getOrDefault(parameterKey,
                parameterKey.substring(0, 1).toUpperCase() +
                        parameterKey.substring(1).replace("_", " ")
        );
    }

    private String translateParameterName(String bulgarianName) {
        Map<String, String> translations = Map.ofEntries(
                Map.entry("Цвят", "Color"),
                Map.entry("Мерна единица", "Unit"),
                Map.entry("Модел", "Model"),
                Map.entry("Резолюция", "Resolution"),
                Map.entry("IR подсветка", "IR Illumination"),
                Map.entry("Размери", "Dimensions"),
                Map.entry("Звук", "Audio"),
                Map.entry("WDR", "WDR"),
                Map.entry("Обектив", "Lens"),
                Map.entry("Корпус", "Body Type"),
                Map.entry("Степен на защита", "Protection Rating"),
                Map.entry("Компресия", "Compression"),
                Map.entry("PoE портове", "PoE Ports"),
                Map.entry("Брой изходи", "Number of Outputs"),
                Map.entry("Работен ток", "Operating Current"),
                Map.entry("Мощност", "Power"),
                Map.entry("Еco серия", "Eco Series")
        );

        return translations.getOrDefault(bulgarianName, bulgarianName);
    }

    private Integer getParameterOrder(String parameterKey) {
        Map<String, Integer> orderMap = Map.ofEntries(
                Map.entry("model", 1),
                Map.entry("rezolyutsiya", 2),
                Map.entry("obektiv", 3),
                Map.entry("korpus", 4),
                Map.entry("cvjat", 5),
                Map.entry("razmer", 6),
                Map.entry("stepen_na_zashtita", 7),
                Map.entry("ir_podsvetka", 8),
                Map.entry("zvuk", 9),
                Map.entry("wdr", 10),
                Map.entry("kompresiya", 11),
                Map.entry("poe_portove", 12),
                Map.entry("moshtnost", 13),
                Map.entry("raboten_tok", 14),
                Map.entry("broy_izhodi", 15),
                Map.entry("seriya_eco", 16),
                Map.entry("merna", 99)
        );

        return orderMap.getOrDefault(parameterKey, 50);
    }

    private Manufacturer createTekraManufacturer(String manufacturerName) {
        try {
            Manufacturer manufacturer = new Manufacturer();
            manufacturer.setName(manufacturerName);
            manufacturer.setInformationName(manufacturerName);
            return manufacturer;
        } catch (Exception e) {
            log.error("Error creating Tekra manufacturer: {}", manufacturerName, e);
            return null;
        }
    }

    // Vali API helper methods

    private Category createCategoryFromExternal(CategoryRequestFromExternalDto extCategory) {
        Category category = new Category();
        category.setExternalId(extCategory.getId());
        category.setShow(extCategory.getShow());
        category.setSortOrder(extCategory.getOrder());

        if (extCategory.getName() != null) {
            extCategory.getName().forEach(name -> {
                if ("bg".equals(name.getLanguageCode())) {
                    category.setNameBg(name.getText());
                } else if ("en".equals(name.getLanguageCode())) {
                    category.setNameEn(name.getText());
                }
            });
        }

        // НОВО: Използваме generateUniqueSlugForVali() за защита от дубликати
        String baseName = category.getNameEn() != null ? category.getNameEn() : category.getNameBg();
        category.setSlug(generateUniqueSlugForVali(baseName, category));

        return category;
    }

    private void updateCategoryFromExternal(Category category, CategoryRequestFromExternalDto extCategory) {
        category.setShow(extCategory.getShow());
        category.setSortOrder(extCategory.getOrder());

        String oldNameBg = category.getNameBg();
        String oldNameEn = category.getNameEn();

        if (extCategory.getName() != null) {
            extCategory.getName().forEach(name -> {
                if ("bg".equals(name.getLanguageCode())) {
                    category.setNameBg(name.getText());
                } else if ("en".equals(name.getLanguageCode())) {
                    category.setNameEn(name.getText());
                }
            });
        }

        // НОВО: Ако името се е променило или slug липсва, генерираме нов уникален slug
        boolean nameChanged = !category.getNameBg().equals(oldNameBg) ||
                (category.getNameEn() != null && !category.getNameEn().equals(oldNameEn));

        if (category.getSlug() == null || category.getSlug().isEmpty() || nameChanged) {
            String baseName = category.getNameEn() != null ? category.getNameEn() : category.getNameBg();
            category.setSlug(generateUniqueSlugForVali(baseName, category));
        }
    }

    /**
     * Генерира уникален slug за Vali API категории
     * Използва същата логика като generateUniqueSlug() но адаптирана за Vali
     */
    private String generateUniqueSlugForVali(String categoryName, Category category) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return "category-" + System.currentTimeMillis();
        }

        // Базов slug от името
        String baseSlug = createSlugFromName(categoryName);

        // Проверка дали базовият slug е свободен
        if (!slugExistsInDatabaseForVali(baseSlug, category.getId())) {
            return baseSlug;
        }

        // Ако има конфликт, опитай с дискриминатор от името
        String discriminator = extractDiscriminator(categoryName);
        if (discriminator != null && !discriminator.isEmpty()) {
            String discriminatedSlug = baseSlug + "-" + discriminator;
            if (!slugExistsInDatabaseForVali(discriminatedSlug, category.getId())) {
                return discriminatedSlug;
            }
        }

        // Ако все още има конфликт, добави числов суфикс
        int counter = 1;
        String numberedSlug;
        do {
            numberedSlug = baseSlug + "-" + counter;
            counter++;
        } while (slugExistsInDatabaseForVali(numberedSlug, category.getId()));

        log.warn("Had to use numbered slug for Vali category '{}': {}", categoryName, numberedSlug);
        return numberedSlug;
    }

    /**
     * Проверява дали slug съществува в базата за Vali категории
     * excludeId позволява да се игнорира собствения ID на категорията при update
     */
    private boolean slugExistsInDatabaseForVali(String slug, Long excludeId) {
        List<Category> existing = categoryRepository.findAll().stream()
                .filter(cat -> slug.equals(cat.getSlug()))
                .toList();

        if (existing.isEmpty()) {
            return false;
        }

        // Ако има excludeId (update на съществуваща категория), игнорираме я
        if (excludeId != null) {
            existing = existing.stream()
                    .filter(cat -> !cat.getId().equals(excludeId))
                    .toList();
        }

        return !existing.isEmpty();
    }

    private void updateCategoryParents(List<CategoryRequestFromExternalDto> externalCategories, Map<Long, Category> existingCategories) {
        for (CategoryRequestFromExternalDto extCategory : externalCategories) {
            if (extCategory.getParent() != null && extCategory.getParent() != 0) {
                Category category = existingCategories.get(extCategory.getId());
                Category parent = existingCategories.get(extCategory.getParent());

                if (category != null && parent != null) {
                    category.setParent(parent);
                    categoryRepository.save(category);
                }
            }
        }
    }

    private Manufacturer createManufacturerFromExternal(ManufacturerRequestDto extManufacturer) {
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setExternalId(extManufacturer.getId());
        manufacturer.setName(extManufacturer.getName());

        if (extManufacturer.getInformation() != null) {
            manufacturer.setInformationName(extManufacturer.getInformation().getName());
            manufacturer.setInformationEmail(extManufacturer.getInformation().getEmail());
            manufacturer.setInformationAddress(extManufacturer.getInformation().getAddress());
        }

        if (extManufacturer.getEuRepresentative() != null) {
            manufacturer.setEuRepresentativeName(extManufacturer.getEuRepresentative().getName());
            manufacturer.setEuRepresentativeEmail(extManufacturer.getEuRepresentative().getEmail());
            manufacturer.setEuRepresentativeAddress(extManufacturer.getEuRepresentative().getAddress());
        }

        return manufacturer;
    }

    private void updateManufacturerFromExternal(Manufacturer manufacturer, ManufacturerRequestDto extManufacturer) {
        manufacturer.setName(extManufacturer.getName());

        if (extManufacturer.getInformation() != null) {
            manufacturer.setInformationName(extManufacturer.getInformation().getName());
            manufacturer.setInformationEmail(extManufacturer.getInformation().getEmail());
            manufacturer.setInformationAddress(extManufacturer.getInformation().getAddress());
        }

        if (extManufacturer.getEuRepresentative() != null) {
            manufacturer.setEuRepresentativeName(extManufacturer.getEuRepresentative().getName());
            manufacturer.setEuRepresentativeEmail(extManufacturer.getEuRepresentative().getEmail());
            manufacturer.setEuRepresentativeAddress(extManufacturer.getEuRepresentative().getAddress());
        }
    }

    private Parameter createParameterFromExternal(ParameterRequestDto extParameter, Category category) {
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

    private void updateParameterFromExternal(Parameter parameter, ParameterRequestDto extParameter) {
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

    private CategorySyncResult syncProductsByCategory(Category category) {
        long totalProcessed = 0, created = 0, updated = 0, errors = 0;

        try {
            Map<Long, Manufacturer> manufacturersMap = manufacturerRepository.findAll()
                    .stream()
                    .collect(Collectors.toMap(Manufacturer::getExternalId, m -> m));

            List<ProductRequestDto> allProducts = valiApiService.getProductsByCategory(category.getExternalId());

            if (allProducts.isEmpty()) {
                log.debug("No products found for category {}", category.getExternalId());
                return new CategorySyncResult(0, 0, 0, 0);
            }

            List<List<ProductRequestDto>> chunks = partitionList(allProducts, batchSize);

            for (int i = 0; i < chunks.size(); i++) {
                List<ProductRequestDto> chunk = chunks.get(i);

                try {
                    ChunkResult result = processProductsChunk(chunk, manufacturersMap);
                    totalProcessed += result.processed;
                    created += result.created;
                    updated += result.updated;
                    errors += result.errors;

                    if (i < chunks.size() - 1) {
                        Thread.sleep(200);
                    }

                } catch (Exception e) {
                    log.error("Error processing product chunk {}/{} for category {}: {}",
                            i + 1, chunks.size(), category.getExternalId(), e.getMessage());
                    errors += chunk.size();
                }
            }

        } catch (Exception e) {
            log.error("Error getting products for category {}: {}", category.getExternalId(), e.getMessage());
            errors++;
        }

        return new CategorySyncResult(totalProcessed, created, updated, errors);
    }

    private ChunkResult processProductsChunk(List<ProductRequestDto> products, Map<Long, Manufacturer> manufacturersMap) {
        long processed = 0, created = 0, updated = 0, errors = 0;
        long chunkStartTime = System.currentTimeMillis();

        for (ProductRequestDto extProduct : products) {
            try {
                Optional<Product> existingProduct = productRepository.findByExternalId(extProduct.getId());

                if (existingProduct.isPresent()) {
                    updateProductFromExternal(existingProduct.get(), extProduct, manufacturersMap);
                    updated++;
                } else {
                    createProductFromExternal(extProduct, manufacturersMap);
                    created++;
                }

                processed++;

                if (processed % 10 == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }

                if ((System.currentTimeMillis() - chunkStartTime) > (maxChunkDurationMinutes * 60 * 1000)) {
                    log.warn("Product chunk processing taking too long, will continue in next chunk");
                    break;
                }

            } catch (Exception e) {
                errors++;
                log.error("Error processing product {}: {}", extProduct.getId(), e.getMessage());
            }
        }

        entityManager.flush();
        entityManager.clear();

        return new ChunkResult(processed, created, updated, errors);
    }

    private void createProductFromExternal(ProductRequestDto extProduct, Map<Long, Manufacturer> manufacturersMap) {
        Manufacturer manufacturer = manufacturersMap.get(extProduct.getManufacturerId());

        if (manufacturer == null) {
            log.warn("Manufacturer not found for product {}: {}. Creating product without manufacturer.",
                    extProduct.getId(), extProduct.getManufacturerId());
        }

        Product product = new Product();
        product.setId(null);
        updateProductFieldsFromExternal(product, extProduct, manufacturer);

        try {
            productRepository.save(product);
        } catch (Exception e) {
            log.error("Failed to create product with externalId {}: {}",
                    extProduct.getId(), e.getMessage());
            throw e;
        }
    }

    private void updateProductFromExternal(Product product, ProductRequestDto extProduct, Map<Long, Manufacturer> manufacturersMap) {
        Manufacturer manufacturer = manufacturersMap.get(extProduct.getManufacturerId());

        if (manufacturer == null) {
            log.warn("Manufacturer not found for product {}: {}. Updating product without manufacturer.",
                    extProduct.getId(), extProduct.getManufacturerId());
        }

        updateProductFieldsFromExternal(product, extProduct, manufacturer);

        try {
            productRepository.save(product);
        } catch (Exception e) {
            log.error("Failed to update product with externalId {}: {}", extProduct.getId(), e.getMessage());
            throw e;
        }
    }

    private void updateProductFieldsFromExternal(Product product, ProductRequestDto extProduct, Manufacturer manufacturer) {
        product.setExternalId(extProduct.getId());
        product.setWorkflowId(extProduct.getIdWF());
        product.setReferenceNumber(extProduct.getReferenceNumber());
        product.setModel(extProduct.getModel());
        product.setBarcode(extProduct.getBarcode());
        product.setManufacturer(manufacturer);
        product.setStatus(ProductStatus.fromCode(extProduct.getStatus()));
        product.setPriceClient(extProduct.getPriceClient());
        product.setPricePartner(extProduct.getPricePartner());
        product.setPricePromo(extProduct.getPricePromo());
        product.setPriceClientPromo(extProduct.getPriceClientPromo());
        product.setShow(extProduct.getShow());
        product.setWarranty(extProduct.getWarranty());
        product.setWeight(extProduct.getWeight());

        setCategoryToProduct(product, extProduct);
        setImagesToProduct(product, extProduct);
        setNamesToProduct(product, extProduct);
        setDescriptionToProduct(product, extProduct);
        setParametersToProduct(product, extProduct);
        product.calculateFinalPrice();
    }

    private void setCategoryToProduct(Product product, ProductRequestDto extProduct) {
        if (extProduct.getCategories() != null && !extProduct.getCategories().isEmpty()) {
            categoryRepository.findByExternalId(extProduct.getCategories().get(0).getId())
                    .ifPresent(product::setCategory);
        }
    }

    private static void setImagesToProduct(Product product, ProductRequestDto extProduct) {
        if (extProduct.getImages() != null && !extProduct.getImages().isEmpty()) {
            product.setPrimaryImageUrl(extProduct.getImages().get(0).getHref());

            List<String> newAdditionalImages = extProduct.getImages().stream()
                    .skip(1)
                    .map(ImageDto::getHref)
                    .toList();

            if (product.getAdditionalImages() != null) {
                product.getAdditionalImages().clear();
                product.getAdditionalImages().addAll(newAdditionalImages);
            } else {
                product.setAdditionalImages(new ArrayList<>(newAdditionalImages));
            }
        } else {
            product.setPrimaryImageUrl(null);
            if (product.getAdditionalImages() != null) {
                product.getAdditionalImages().clear();
            } else {
                product.setAdditionalImages(new ArrayList<>());
            }
        }
    }

    private static void setNamesToProduct(Product product, ProductRequestDto extProduct) {
        if (extProduct.getName() != null) {
            extProduct.getName().forEach(name -> {
                switch (name.getLanguageCode()) {
                    case "bg" -> product.setNameBg(name.getText());
                    case "en" -> product.setNameEn(name.getText());
                }
            });
        }
    }

    private static void setDescriptionToProduct(Product product, ProductRequestDto extProduct) {
        if (extProduct.getDescription() != null) {
            extProduct.getDescription().forEach(desc -> {
                switch (desc.getLanguageCode()) {
                    case "bg" -> product.setDescriptionBg(desc.getText());
                    case "en" -> product.setDescriptionEn(desc.getText());
                }
            });
        }
    }

    private <T> List<List<T>> partitionList(List<T> list, int chunkSize) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            chunks.add(list.subList(i, Math.min(i + chunkSize, list.size())));
        }
        return chunks;
    }

    private String generateSlug(String nameEn, String nameBg) {
        String name = nameEn;

        if (name == null || name.trim().isEmpty()) {
            name = nameBg;
        }

        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        return createSlugFromName(name);
    }

    private String createSlugFromName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        String transliterated = transliterateCyrillic(name.trim());

        return transliterated.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    private static final Map<String, String> CYRILLIC_TO_LATIN = Map.ofEntries(
            Map.entry("А", "A"), Map.entry("Б", "B"), Map.entry("В", "V"),
            Map.entry("Г", "G"), Map.entry("Д", "D"), Map.entry("Е", "E"),
            Map.entry("Ж", "Zh"), Map.entry("З", "Z"), Map.entry("И", "I"),
            Map.entry("Й", "Y"), Map.entry("К", "K"), Map.entry("Л", "L"),
            Map.entry("М", "M"), Map.entry("Н", "N"), Map.entry("О", "O"),
            Map.entry("П", "P"), Map.entry("Р", "R"), Map.entry("С", "S"),
            Map.entry("Т", "T"), Map.entry("У", "U"), Map.entry("Ф", "F"),
            Map.entry("Х", "Kh"), Map.entry("Ц", "Ts"), Map.entry("Ч", "Ch"),
            Map.entry("Ш", "Sh"), Map.entry("Щ", "Sht"), Map.entry("Ъ", "A"),
            Map.entry("Ю", "Yu"), Map.entry("Я", "Ya"),
            Map.entry("а", "a"), Map.entry("б", "b"), Map.entry("в", "v"),
            Map.entry("г", "g"), Map.entry("д", "d"), Map.entry("е", "e"),
            Map.entry("ж", "zh"), Map.entry("з", "z"), Map.entry("и", "i"),
            Map.entry("й", "y"), Map.entry("к", "k"), Map.entry("л", "l"),
            Map.entry("м", "m"), Map.entry("н", "n"), Map.entry("о", "o"),
            Map.entry("п", "p"), Map.entry("р", "r"), Map.entry("с", "s"),
            Map.entry("т", "t"), Map.entry("у", "u"), Map.entry("ф", "f"),
            Map.entry("х", "kh"), Map.entry("ц", "ts"), Map.entry("ч", "ch"),
            Map.entry("ш", "sh"), Map.entry("щ", "sht"), Map.entry("ъ", "a"),
            Map.entry("ю", "yu"), Map.entry("я", "ya")
    );

    private String transliterateCyrillic(String text) {
        if (text == null) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String character = String.valueOf(text.charAt(i));
            result.append(CYRILLIC_TO_LATIN.getOrDefault(character, character));
        }

        return result.toString();
    }

    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                String strValue = ((String) value).trim();
                return strValue.isEmpty() ? null : Double.parseDouble(strValue);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Integer getIntegerValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                String strValue = ((String) value).trim();
                return strValue.isEmpty() ? null : Integer.parseInt(strValue);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        String str = value.toString().trim();
        return str.isEmpty() ? null : str;
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString().trim() : null;
    }

    private SyncLog createSyncLogSimple(String syncType) {
        try {
            SyncLog syncLog = new SyncLog();
            syncLog.setSyncType(syncType);
            syncLog.setStatus(LOG_STATUS_IN_PROGRESS);
            return syncLogRepository.save(syncLog);
        } catch (Exception e) {
            log.error("Failed to create sync log: {}", e.getMessage());
            SyncLog dummyLog = new SyncLog();
            dummyLog.setId(-1L);
            dummyLog.setSyncType(syncType);
            return dummyLog;
        }
    }

    private void updateSyncLogSimple(SyncLog syncLog, String status, long processed,
                                     long created, long updated, long errors,
                                     String errorMessage, long startTime) {
        try {
            if (syncLog.getId() != null && syncLog.getId() > 0) {
                syncLog.setStatus(status);
                syncLog.setRecordsProcessed(processed);
                syncLog.setRecordsCreated(created);
                syncLog.setRecordsUpdated(updated);
                syncLog.setDurationMs(System.currentTimeMillis() - startTime);

                if (errorMessage != null) {
                    syncLog.setErrorMessage(errorMessage);
                }

                syncLogRepository.save(syncLog);
            }
        } catch (Exception e) {
            log.error("Failed to update sync log: {}", e.getMessage());
        }
    }

    public void syncProductsByCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + categoryId));

        log.info("Starting sync for single category: {}", categoryId);
        CategorySyncResult result = syncProductsByCategory(category);
        log.info("Single category sync completed - Processed: {}, Created: {}, Updated: {}, Errors: {}",
                result.processed, result.created, result.updated, result.errors);
    }

    public void fetchAll() {
        log.info("Starting manual full synchronization");
        syncCategories();
        syncManufacturers();
        syncParameters();
        syncProducts();
        log.info("Manual full synchronization completed");
    }

    @Transactional
    public void fixDuplicateCategories() {
        log.info("Checking for duplicate Tekra categories...");

        List<Category> allTekraCategories = categoryRepository.findAll().stream()
                .filter(cat -> cat.getTekraSlug() != null)
                .toList();

        Map<String, List<Category>> categoriesByName = allTekraCategories.stream()
                .collect(Collectors.groupingBy(Category::getNameBg));

        int duplicatesFound = 0;
        int duplicatesRemoved = 0;

        for (Map.Entry<String, List<Category>> entry : categoriesByName.entrySet()) {
            String name = entry.getKey();
            List<Category> categories = entry.getValue();

            if (categories.size() > 1) {
                duplicatesFound++;
                log.warn("Found {} duplicate categories with name: '{}'", categories.size(), name);

                Category categoryToKeep = categories.stream()
                        .max(Comparator.comparing(cat -> cat.getId()))
                        .orElse(categories.get(0));

                log.info("Keeping category with ID: {}, tekra_id: {}, slug: {}",
                        categoryToKeep.getId(), categoryToKeep.getTekraId(), categoryToKeep.getTekraSlug());

                for (Category duplicate : categories) {
                    if (!duplicate.getId().equals(categoryToKeep.getId())) {
                        try {
                            long productCount = productRepository.countByCategoryId(duplicate.getId());
                            if (productCount > 0) {
                                log.warn("Category {} has {} products, reassigning to kept category",
                                        duplicate.getId(), productCount);
                                productRepository.updateCategoryForProducts(duplicate.getId(), categoryToKeep.getId());
                            }

                            categoryRepository.delete(duplicate);
                            duplicatesRemoved++;
                            log.info("Deleted duplicate category with ID: {}", duplicate.getId());
                        } catch (Exception e) {
                            log.error("Error deleting duplicate category {}: {}", duplicate.getId(), e.getMessage());
                        }
                    }
                }
            }
        }

        log.info("Duplicate categories cleanup complete - Found: {}, Removed: {}",
                duplicatesFound, duplicatesRemoved);
    }

    private static class ChunkResult {
        long processed, created, updated, errors;

        ChunkResult(long processed, long created, long updated, long errors) {
            this.processed = processed;
            this.created = created;
            this.updated = updated;
            this.errors = errors;
        }
    }

    private static class CategorySyncResult {
        long processed, created, updated, errors;

        CategorySyncResult(long processed, long created, long updated, long errors) {
            this.processed = processed;
            this.created = created;
            this.updated = updated;
            this.errors = errors;
        }
    }

    private void analyzeCategoryPaths() {
        log.info("=== CATEGORY PATH ANALYSIS ===");

        List<Category> allCategories = categoryRepository.findAll();

        Map<String, Long> pathCounts = allCategories.stream()
                .filter(cat -> cat.getCategoryPath() != null)
                .collect(Collectors.groupingBy(Category::getCategoryPath, Collectors.counting()));

        log.info("Total categories: {}", allCategories.size());
        log.info("Categories with paths: {}", pathCounts.size());

        // Показваме категории с еднакви имена но различни пътища
        Map<String, List<Category>> byName = allCategories.stream()
                .filter(cat -> cat.getNameBg() != null)
                .collect(Collectors.groupingBy(Category::getNameBg));

        byName.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .forEach(e -> {
                    log.info("Duplicate name '{}' has {} categories:", e.getKey(), e.getValue().size());
                    e.getValue().forEach(cat ->
                            log.info("  - Path: '{}', ID: {}", cat.getCategoryPath(), cat.getId())
                    );
                });

        log.info("==============================");
    }

    private void logProductCategoryMapping(Map<String, Object> product, Category matchedCategory) {
        String cat1 = getStringValue(product, "category_1");
        String cat2 = getStringValue(product, "category_2");
        String cat3 = getStringValue(product, "category_3");
        String sku = getStringValue(product, "sku");

        String expectedPath = buildCategoryPath(cat1, cat2, cat3);

        log.info("Product: {} | XML Path: {} | Matched: {} ({})",
                sku,
                expectedPath,
                matchedCategory != null ? matchedCategory.getNameBg() : "NULL",
                matchedCategory != null ? matchedCategory.getCategoryPath() : "N/A"
        );
    }

    /**
     * Валидира йерархията след sync
     */
    private void validateCategoryHierarchy() {
        log.info("=== VALIDATING CATEGORY HIERARCHY ===");

        List<Category> allCategories = categoryRepository.findAll().stream()
                .filter(cat -> cat.getTekraSlug() != null)
                .toList();

        int orphans = 0;
        int valid = 0;

        for (Category cat : allCategories) {
            if (cat.getParent() != null) {
                Long parentId = cat.getParent().getId();

                boolean parentExists = categoryRepository.existsById(parentId);

                if (!parentExists) {
                    log.error("✗ ORPHAN: '{}' (ID: {}) has parent_id={} which DOES NOT EXIST!",
                            cat.getNameBg(), cat.getId(), parentId);
                    orphans++;
                } else {
                    valid++;
                }
            }
        }

        log.info("Validation complete: {} valid, {} orphans", valid, orphans);

        if (orphans > 0) {
            log.error("⚠ Found {} orphan categories! Check logs above.", orphans);
        }

        log.info("====================================");
    }

    /**
     * ✅ ВРЕМЕНЕН DEBUG метод
     * Използвай го за да видиш ТОЧНО каква структура връща Tekra API
     * Добави го в SyncService.java и го извикай преди syncTekraCategories()
     */
    public void debugTekraStructure() {
        log.info("=== DEBUG: TEKRA CATEGORY STRUCTURE ===");

        try {
            List<Map<String, Object>> externalCategories = tekraApiService.getCategoriesRaw();

            // Намираме videonablyudenie
            Map<String, Object> mainCategory = externalCategories.stream()
                    .filter(cat -> "videonablyudenie".equals(getString(cat, "slug")))
                    .findFirst()
                    .orElse(null);

            if (mainCategory == null) {
                log.error("videonablyudenie not found!");
                return;
            }

            log.info("MAIN: {} (id: {}, slug: {})",
                    getString(mainCategory, "name"),
                    getString(mainCategory, "id"),
                    getString(mainCategory, "slug"));

            // Виж level-2 категориите
            Object subCategoriesObj = mainCategory.get("sub_categories");
            if (subCategoriesObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> subCategories = (List<Map<String, Object>>) subCategoriesObj;

                log.info("Found {} LEVEL-2 categories:", subCategories.size());

                for (Map<String, Object> subCat : subCategories) {
                    String name = getString(subCat, "name");
                    String slug = getString(subCat, "slug");
                    String id = getString(subCat, "id");
                    String count = getString(subCat, "count");

                    log.info("  L2: '{}' (id: {}, slug: {}, products: {})",
                            name, id, slug, count);

                    // Виж level-3 категориите
                    Object subSubCatObj = subCat.get("subsubcat");
                    if (subSubCatObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> subSubCategories = (List<Map<String, Object>>) subSubCatObj;

                        for (Map<String, Object> subSubCat : subSubCategories) {
                            String subName = getString(subSubCat, "name");
                            String subSlug = getString(subSubCat, "slug");
                            String subId = getString(subSubCat, "id");
                            String subCount = getString(subSubCat, "count");

                            log.info("    └─ L3: '{}' (id: {}, slug: {}, products: {})",
                                    subName, subId, subSlug, subCount);
                        }
                    }
                }
            }

            log.info("=======================================");

        } catch (Exception e) {
            log.error("Debug failed: {}", e.getMessage(), e);
        }
    }

    /**
     * ✅ Debug метод - Търси специално "HD аналогови системи" в Tekra API
     */
    public void debugHdAnalogCategory() {
        log.info("=== DEBUG: Searching for HD Analog category in Tekra ===");

        try {
            List<Map<String, Object>> externalCategories = tekraApiService.getCategoriesRaw();

            Map<String, Object> mainCategory = externalCategories.stream()
                    .filter(cat -> "videonablyudenie".equals(getString(cat, "slug")))
                    .findFirst()
                    .orElse(null);

            if (mainCategory == null) {
                log.error("Main category not found!");
                return;
            }

            Object subCategoriesObj = mainCategory.get("sub_categories");
            if (!(subCategoriesObj instanceof List)) {
                log.error("No sub_categories found!");
                return;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> subCategories = (List<Map<String, Object>>) subCategoriesObj;

            log.info("Total level-2 categories: {}", subCategories.size());

            // Търсим "HD аналогови" или подобни
            for (Map<String, Object> subCat : subCategories) {
                String name = getString(subCat, "name");
                String slug = getString(subCat, "slug");
                String id = getString(subCat, "id");

                log.info("L2: name='{}', slug='{}', id='{}'", name, slug, id);

                // Проверяваме дали има подкатегории
                Object subSubCatObj = subCat.get("subsubcat");
                if (subSubCatObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> subSubCategories = (List<Map<String, Object>>) subSubCatObj;

                    log.info("  └─ Has {} level-3 categories:", subSubCategories.size());

                    for (Map<String, Object> subSubCat : subSubCategories) {
                        String subName = getString(subSubCat, "name");
                        String subSlug = getString(subSubCat, "slug");
                        log.info("     - '{}' (slug: {})", subName, subSlug);
                    }
                }
            }

            // Специално търсене за "Камери" и "Записващи устройства"
            log.info("\n=== Searching for 'Камери' in all categories ===");
            for (Map<String, Object> subCat : subCategories) {
                Object subSubCatObj = subCat.get("subsubcat");
                if (subSubCatObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> subSubCategories = (List<Map<String, Object>>) subSubCatObj;

                    for (Map<String, Object> subSubCat : subSubCategories) {
                        String subName = getString(subSubCat, "name");
                        if (subName != null && subName.toLowerCase().contains("камер")) {
                            log.info("Found 'Камери' under: '{}' (slug: {})",
                                    getString(subCat, "name"),
                                    getString(subCat, "slug"));
                        }
                    }
                }
            }

            log.info("\n=== Searching for 'Записващи устройства' ===");
            for (Map<String, Object> subCat : subCategories) {
                Object subSubCatObj = subCat.get("subsubcat");
                if (subSubCatObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> subSubCategories = (List<Map<String, Object>>) subSubCatObj;

                    for (Map<String, Object> subSubCat : subSubCategories) {
                        String subName = getString(subSubCat, "name");
                        if (subName != null && subName.toLowerCase().contains("записващ")) {
                            log.info("Found 'Записващи устройства' under: '{}' (slug: {})",
                                    getString(subCat, "name"),
                                    getString(subCat, "slug"));
                        }
                    }
                }
            }

            log.info("==============================================");

        } catch (Exception e) {
            log.error("Debug failed: {}", e.getMessage(), e);
        }
    }

// ═══════════════════════════════════════════════════════════════
// ИЗПОЛЗВАЙ ТАКА:
//
// @GetMapping("/admin/debug-tekra")
// public String debugTekra() {
//     syncService.debugTekraStructure();
//     return "Check logs";
// }
// ═══════════════════════════════════════════════════════════════
}