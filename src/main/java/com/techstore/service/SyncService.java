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
            log.info("Starting Tekra categories synchronization with nested subcategories");

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
                log.warn("Category with slug 'videonablyudenie' not found in Tekra API response");
                updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, 0, 0, 0, 0, "videonablyudenie category not found", startTime);
                return;
            }

            log.info("Found main category 'videonablyudenie' with id: {}", getString(mainCategory, "id"));

            Map<String, Category> existingCategories = categoryRepository.findAll()
                    .stream()
                    .filter(cat -> cat.getTekraSlug() != null)
                    .collect(Collectors.toMap(
                            cat -> cat.getSlug() != null ? cat.getSlug() : cat.getTekraId(),
                            cat -> cat,
                            (existing, duplicate) -> {
                                log.warn("Duplicate category found: keeping ID {}, discarding ID {}",
                                        existing.getId(), duplicate.getId());
                                return existing;
                            }
                    ));

            long created = 0, updated = 0, skipped = 0;

            // Step 1: Create/update main category
            Category mainCat = createOrUpdateTekraCategory(mainCategory, existingCategories, null);
            if (mainCat != null) {
                String mainKey = mainCat.getSlug();
                if (existingCategories.containsKey(mainKey)) {
                    updated++;
                } else {
                    created++;
                    existingCategories.put(mainKey, mainCat);
                }
            }

            // ВАЖНО: Създаваме задължителните структурни категории които Tekra не връща
            Category hdAnalogCategory = createMissingStructuralCategory(
                    "HD аналогови системи", "hd-analogovi-sistemi", mainCat, existingCategories
            );
            if (hdAnalogCategory != null) {
                created++;
                log.info("Created missing structural category: HD аналогови системи");
            }

            Category accessoriesCategory = createMissingStructuralCategory(
                    "Аксесоари", "aksesoari-root", mainCat, existingCategories
            );
            if (accessoriesCategory != null) {
                created++;
                log.info("Created missing structural category: Аксесоари");
            }

            // Step 2: Process sub_categories (level 2)
            Object subCategoriesObj = mainCategory.get("sub_categories");
            if (subCategoriesObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> subCategories = (List<Map<String, Object>>) subCategoriesObj;

                log.info("Found {} level-2 subcategories", subCategories.size());

                for (Map<String, Object> subCat : subCategories) {
                    try {
                        String subCatId = getString(subCat, "id");
                        String subCatSlug = getString(subCat, "slug");
                        String subCatName = getString(subCat, "name");

                        if (subCatId == null || subCatSlug == null || subCatName == null) {
                            log.warn("Skipping subcategory with missing fields");
                            skipped++;
                            continue;
                        }

                        // ВАЖНА ЛОГИКА: Определяме parent-а според името на категорията
                        Category parentForThisCategory = determineCorrectParent(
                                subCatName, mainCat, hdAnalogCategory, accessoriesCategory
                        );

                        Category level2Cat = createOrUpdateTekraCategory(subCat, existingCategories, parentForThisCategory);
                        if (level2Cat != null) {
                            String level2Key = level2Cat.getSlug();
                            if (existingCategories.containsKey(level2Key)) {
                                updated++;
                            } else {
                                created++;
                                existingCategories.put(level2Key, level2Cat);
                            }

                            // Step 3: Process subsubcat (level 3)
                            Object subSubCatObj = subCat.get("subsubcat");
                            if (subSubCatObj instanceof List) {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> subSubCategories = (List<Map<String, Object>>) subSubCatObj;

                                for (Map<String, Object> subSubCat : subSubCategories) {
                                    try {
                                        String subSubCatId = getString(subSubCat, "id");
                                        String subSubCatSlug = getString(subSubCat, "slug");
                                        String subSubCatName = getString(subSubCat, "name");

                                        if (subSubCatId == null || subSubCatSlug == null || subSubCatName == null) {
                                            log.warn("Skipping level-3 category with missing fields");
                                            skipped++;
                                            continue;
                                        }

                                        Category level3Cat = createOrUpdateTekraCategory(subSubCat, existingCategories, level2Cat);
                                        if (level3Cat != null) {
                                            String level3Key = level3Cat.getSlug();
                                            if (existingCategories.containsKey(level3Key)) {
                                                updated++;
                                            } else {
                                                created++;
                                                existingCategories.put(level3Key, level3Cat);
                                            }
                                        }

                                    } catch (Exception e) {
                                        log.error("Error processing level-3 category: {}", e.getMessage());
                                        skipped++;
                                    }
                                }
                            }
                        }

                    } catch (Exception e) {
                        log.error("Error processing subcategory: {}", e.getMessage());
                        skipped++;
                    }
                }
            }

            long totalCategories = created + updated;
            updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, totalCategories, created, updated, skipped,
                    skipped > 0 ? String.format("Skipped %d categories with errors", skipped) : null, startTime);

            log.info("Tekra categories synchronization completed - Total: {}, Created: {}, Updated: {}, Skipped: {}",
                    totalCategories, created, updated, skipped);

        } catch (Exception e) {
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
            log.info("=== STARTING Tekra products synchronization ===");

            fixDuplicateProducts();

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
                    log.info("Fetching products for category: {} (slug: {})",
                            category.getNameBg(), categorySlug);

                    List<Map<String, Object>> categoryProducts = tekraApiService.getProductsRaw(categorySlug);
                    log.info("Found {} products in category '{}'", categoryProducts.size(), category.getNameBg());

                    for (Map<String, Object> product : categoryProducts) {
                        String sku = getStringValue(product, "sku");
                        if (sku != null && !processedSkus.contains(sku)) {
                            allProducts.add(product);
                            processedSkus.add(sku);

                            // ВАЖНО: Запазваме пълната информация за source категорията
                            product.put("source_category_slug", categorySlug);
                            product.put("source_category_name", category.getNameBg());
                            product.put("source_category_id", category.getId());

                            // Запазваме и parent информация за точно мачване
                            if (category.getParent() != null) {
                                product.put("source_category_parent_id", category.getParent().getId());
                                product.put("source_category_parent_slug", category.getParent().getTekraSlug());
                                product.put("source_category_parent_name", category.getParent().getNameBg());
                            }
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

            // STEP 2: Load categories for mapping
            log.info("STEP 2: Loading categories for mapping...");
            Map<String, Category> categoriesByName = new HashMap<>();
            Map<String, Category> categoriesBySlug = new HashMap<>();
            Map<String, Category> categoriesByTekraSlug = new HashMap<>();

            for (Category cat : allCategories) {
                if (cat.getNameBg() != null) {
                    categoriesByName.put(cat.getNameBg().toLowerCase(), cat);
                    String normalized = normalizeCategoryName(cat.getNameBg());
                    if (!categoriesByName.containsKey(normalized)) {
                        categoriesByName.put(normalized, cat);
                    }
                }

                if (cat.getSlug() != null) {
                    categoriesBySlug.put(cat.getSlug().toLowerCase(), cat);
                }

                if (cat.getTekraSlug() != null) {
                    String tekraSlugKey = cat.getTekraSlug().toLowerCase();

                    if (cat.getParent() != null && cat.getParent().getTekraSlug() != null) {
                        String compositeKey = cat.getParent().getTekraSlug().toLowerCase() + ":" + tekraSlugKey;
                        categoriesByTekraSlug.put(compositeKey, cat);
                        log.debug("Mapped category with composite key: '{}' -> '{}'", compositeKey, cat.getNameBg());
                    }

                    if (!categoriesByTekraSlug.containsKey(tekraSlugKey)) {
                        categoriesByTekraSlug.put(tekraSlugKey, cat);
                    } else {
                        log.warn("Duplicate tekra_slug found: '{}' - categories: '{}' and '{}'",
                                tekraSlugKey, categoriesByTekraSlug.get(tekraSlugKey).getNameBg(), cat.getNameBg());
                    }
                }
            }

            log.info("Category maps created: byName={}, bySlug={}, byTekraSlug={}",
                    categoriesByName.size(), categoriesBySlug.size(), categoriesByTekraSlug.size());

            analyzeProductCategories(allProducts, categoriesByName);

            // STEP 3: Process products
            log.info("STEP 3: Processing {} products...", allProducts.size());

            long totalProcessed = 0, totalCreated = 0, totalUpdated = 0, totalErrors = 0;
            long skippedNoCategory = 0;

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

                    Category productCategory = findMostSpecificCategory(rawProduct, categoriesByName,
                            categoriesBySlug, categoriesByTekraSlug);

                    // Валидираме намерената категория
                    if (productCategory != null && !isValidCategory(productCategory)) {
                        log.warn("Invalid category found for product {}, rejecting", sku);
                        productCategory = null;
                    }

                    // АКО НЕ Е НАМЕРЕНА КАТЕГОРИЯ - SKIP продукта
                    // Това гарантира че продуктите винаги отиват на правилното място!
                    if (productCategory == null) {
                        log.warn("✗✗✗ Skipping product '{}' ({}): NO VALID CATEGORY MAPPING", name, sku);
                        skippedNoCategory++;
                        continue;
                    }

                    // Допълнителна валидация - проверяваме дали категорията има правилен parent
                    String sourceCategoryParentName = (String) rawProduct.get("source_category_parent_name");
                    if (sourceCategoryParentName != null && productCategory.getParent() != null) {
                        String actualParentName = productCategory.getParent().getNameBg();
                        if (!actualParentName.toLowerCase().contains(sourceCategoryParentName.toLowerCase()) &&
                                !sourceCategoryParentName.toLowerCase().contains(actualParentName.toLowerCase())) {
                            log.warn("✗ Category parent mismatch for product '{}': expected parent '{}', got '{}'",
                                    sku, sourceCategoryParentName, actualParentName);
                            log.warn("✗ Skipping product to avoid wrong category assignment");
                            skippedNoCategory++;
                            continue;
                        }
                    }

                    log.info("✓✓✓ Product '{}' assigned to category: '{}' (parent: '{}')",
                            sku,
                            productCategory.getNameBg(),
                            productCategory.getParent() != null ? productCategory.getParent().getNameBg() : "ROOT");

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
                        log.info("Progress: {}/{} (created: {}, updated: {}, errors: {}, skipped no category: {})",
                                totalProcessed, allProducts.size(), totalCreated, totalUpdated,
                                totalErrors, skippedNoCategory);
                    }

                    if (totalProcessed % 50 == 0) {
                        entityManager.flush();
                        entityManager.clear();
                    }

                } catch (Exception e) {
                    totalErrors++;
                    log.error("Error processing product {}: {}", getStringValue(rawProduct, "sku"), e.getMessage(), e);
                }
            }

            String message = String.format(
                    "Total: %d, Created: %d, Updated: %d, Skipped (No Category): %d, Errors: %d",
                    totalProcessed, totalCreated, totalUpdated, skippedNoCategory, totalErrors
            );

            updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, totalProcessed, totalCreated,
                    totalUpdated, totalErrors, message, startTime);

            log.info("=== COMPLETE: Products sync finished in {}ms ===", System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, e.getMessage(), startTime);
            log.error("=== FAILED: Products synchronization error ===", e);
            throw e;
        }
    }

    @Transactional
    public void syncTekraComplete() {
        log.info("=== Starting complete Tekra synchronization with subcategories ===");
        long overallStart = System.currentTimeMillis();

        try {
            log.info("Step 1: Syncing Tekra categories and subcategories...");
            syncTekraCategories();

            long categoriesCount = categoryRepository.findAll().stream()
                    .filter(cat -> cat.getTekraSlug() != null)
                    .count();
            log.info("✓ Synced {} Tekra categories (including subcategories)", categoriesCount);

            log.info("Step 2: Syncing Tekra manufacturers from all categories...");
            syncTekraManufacturers();

            log.info("Step 3: Syncing Tekra parameters for all categories...");
            syncTekraParameters();

            if (tekraApiService != null) {
                tekraApiService.clearCache();
            }

            log.info("Step 4: Syncing Tekra products for all categories with parameters...");
            syncTekraProducts();

            long totalDuration = System.currentTimeMillis() - overallStart;
            log.info("=== Complete Tekra synchronization finished in {}ms ({} minutes) ===",
                    totalDuration, totalDuration / 60000);

        } catch (Exception e) {
            long totalDuration = System.currentTimeMillis() - overallStart;
            log.error("=== Complete Tekra synchronization failed after {}ms ===", totalDuration, e);
            throw e;
        }
    }

    // ============ HELPER METHODS FOR STRUCTURAL CATEGORIES ============

    /**
     * Създава липсваща структурна категория (не е от Tekra API)
     */
    private Category createMissingStructuralCategory(String name, String slug,
                                                     Category parent,
                                                     Map<String, Category> existingCategories) {
        // Проверка дали вече съществува
        String fullSlug = parent != null ? parent.getSlug() + "-" + slug : slug;

        if (existingCategories.containsKey(fullSlug)) {
            log.info("Structural category already exists: {}", name);
            return existingCategories.get(fullSlug);
        }

        // Проверка в БД
        Optional<Category> existing = categoryRepository.findAll().stream()
                .filter(cat -> fullSlug.equals(cat.getSlug()))
                .findFirst();

        if (existing.isPresent()) {
            existingCategories.put(fullSlug, existing.get());
            return existing.get();
        }

        // Създаване на нова категория
        Category category = new Category();
        category.setNameBg(name);
        category.setNameEn(name);
        category.setSlug(fullSlug);
        category.setTekraSlug(slug); // Запазваме slug-а за препратки
        category.setParent(parent);
        category.setShow(true);
        category.setSortOrder(0);

        category = categoryRepository.save(category);
        existingCategories.put(fullSlug, category);

        log.info("✓ Created missing structural category: {} (slug: {}, parent: {})",
                name, fullSlug, parent != null ? parent.getNameBg() : "ROOT");

        return category;
    }

    /**
     * Определя правилния parent за категория според името ѝ
     */
    private Category determineCorrectParent(String categoryName,
                                            Category mainCategory,
                                            Category hdAnalogCategory,
                                            Category accessoriesCategory) {
        String nameLower = categoryName.toLowerCase();

        // IP системи винаги е под главната категория
        if (nameLower.contains("ip") && nameLower.contains("систем")) {
            log.debug("Category '{}' → parent: Видеонаблюдение (ROOT)", categoryName);
            return mainCategory;
        }

        // Аксесоари които трябва да са под "Аксесоари" root категория
        if (matchesAccessoryCategory(nameLower)) {
            log.debug("Category '{}' → parent: Аксесоари", categoryName);
            return accessoriesCategory;
        }

        // Камери без "IP" в името → под "HD аналогови системи"
        if (nameLower.contains("камер") && !nameLower.contains("ip")) {
            log.debug("Category '{}' → parent: HD аналогови системи", categoryName);
            return hdAnalogCategory;
        }

        // Записващи устройства без NVR → под "HD аналогови системи"
        if (nameLower.contains("записващ") && !nameLower.contains("nvr") && !nameLower.contains("мрежов")) {
            log.debug("Category '{}' → parent: HD аналогови системи", categoryName);
            return hdAnalogCategory;
        }

        // Мрежови устройства → под "IP системи" (ще се създаде ако липсва)
        if (nameLower.contains("мрежов") && nameLower.contains("устройств")) {
            // Търсим "IP системи" в съществуващите
            Optional<Category> ipSystems = categoryRepository.findAll().stream()
                    .filter(cat -> cat.getNameBg().contains("IP") && cat.getNameBg().contains("систем"))
                    .findFirst();
            if (ipSystems.isPresent()) {
                log.debug("Category '{}' → parent: IP системи", categoryName);
                return ipSystems.get();
            }
        }

        // По подразбиране - под главната категория
        log.debug("Category '{}' → parent: Видеонаблюдение (DEFAULT)", categoryName);
        return mainCategory;
    }

    /**
     * Проверява дали категория е аксесоар
     */
    private boolean matchesAccessoryCategory(String nameLower) {
        String[] accessoryKeywords = {
                "адаптер", "захранващ", "блок", "конектор", "видеобалун",
                "стойк", "основ", "камер", "защит", "изолатор", "друг"
        };

        for (String keyword : accessoryKeywords) {
            if (nameLower.contains(keyword)) {
                // Изключение: "Стойки и основи за камери" е аксесоар
                // Но "Камери" самостоятелно НЕ е аксесоар
                if (keyword.equals("камер") && !nameLower.contains("стойк") && !nameLower.contains("основ")) {
                    return false;
                }
                return true;
            }
        }

        return false;
    }

    private Category createOrUpdateTekraCategory(Map<String, Object> rawData,
                                                 Map<String, Category> existingCategories,
                                                 Category parentCategory) {
        try {
            String tekraId = getString(rawData, "id");
            String tekraSlug = getString(rawData, "slug");
            String name = getString(rawData, "name");

            if (tekraId == null || tekraSlug == null || name == null) {
                log.warn("Cannot create category with missing required fields");
                return null;
            }

            String uniqueSlug = generateUniqueSlug(tekraSlug, name, parentCategory, existingCategories);

            Category category = existingCategories.get(uniqueSlug);

            if (category == null) {
                Optional<Category> foundCategory = findExistingCategoryByTekraData(
                        tekraId, tekraSlug, parentCategory
                );

                if (foundCategory.isPresent()) {
                    category = foundCategory.get();
                    log.debug("Found existing category: {} (slug: {})", name, uniqueSlug);
                } else {
                    category = new Category();
                    category.setTekraId(tekraId);
                    log.info("Creating NEW category: {} (unique slug: {})", name, uniqueSlug);
                }
            }

            category.setTekraSlug(tekraSlug);
            category.setSlug(uniqueSlug);
            category.setNameBg(name);
            category.setNameEn(name);
            category.setParent(parentCategory);

            String countStr = getString(rawData, "count");
            if (countStr != null) {
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

            category = categoryRepository.save(category);

            String parentInfo = parentCategory != null ?
                    " (parent: " + parentCategory.getNameBg() + ")" : " (ROOT)";
            log.info("Saved category: {} {} (unique slug: {}, tekra_slug: {}, id: {})",
                    name, parentInfo, uniqueSlug, tekraSlug, category.getId());

            return category;

        } catch (Exception e) {
            log.error("Error creating/updating category from Tekra data: {}", e.getMessage());
            return null;
        }
    }

    private String generateUniqueSlug(String tekraSlug, String categoryName,
                                      Category parentCategory,
                                      Map<String, Category> existingCategories) {
        String baseSlug = tekraSlug;

        if (parentCategory != null) {
            String hierarchicalSlug = parentCategory.getSlug() + "-" + baseSlug;

            if (!slugExistsInMap(hierarchicalSlug, existingCategories) &&
                    !slugExistsInDatabase(hierarchicalSlug, parentCategory)) {
                return hierarchicalSlug;
            }

            String parentKeyword = extractKeyword(parentCategory.getNameBg());
            if (parentKeyword != null && !parentKeyword.isEmpty()) {
                hierarchicalSlug = baseSlug + "-" + parentKeyword;
                if (!slugExistsInMap(hierarchicalSlug, existingCategories) &&
                        !slugExistsInDatabase(hierarchicalSlug, parentCategory)) {
                    return hierarchicalSlug;
                }
            }
        }

        if (!slugExistsInMap(baseSlug, existingCategories) &&
                !slugExistsInDatabase(baseSlug, parentCategory)) {
            return baseSlug;
        }

        String discriminator = extractDiscriminator(categoryName);
        if (discriminator != null && !discriminator.isEmpty()) {
            String discriminatedSlug = baseSlug + "-" + discriminator;
            if (!slugExistsInMap(discriminatedSlug, existingCategories) &&
                    !slugExistsInDatabase(discriminatedSlug, parentCategory)) {
                return discriminatedSlug;
            }
        }

        int counter = 1;
        String numberedSlug;
        do {
            numberedSlug = baseSlug + "-" + counter;
            counter++;
        } while (slugExistsInMap(numberedSlug, existingCategories) ||
                slugExistsInDatabase(numberedSlug, parentCategory));

        log.warn("Had to use numbered slug for category '{}': {}", categoryName, numberedSlug);
        return numberedSlug;
    }

    private String extractDiscriminator(String categoryName) {
        if (categoryName == null || categoryName.isEmpty()) {
            return null;
        }

        String lowerName = categoryName.toLowerCase();

        Map<String, String> discriminators = Map.ofEntries(
                Map.entry("ip", "ip"),
                Map.entry("аналогов", "analog"),
                Map.entry("мрежов", "network"),
                Map.entry("безжичн", "wireless"),
                Map.entry("wifi", "wifi"),
                Map.entry("poe", "poe"),
                Map.entry("куполн", "dome"),
                Map.entry("булет", "bullet"),
                Map.entry("цилиндричн", "bullet"),
                Map.entry("вътрешн", "indoor"),
                Map.entry("външн", "outdoor"),
                Map.entry("nvr", "nvr"),
                Map.entry("dvr", "dvr"),
                Map.entry("hdcvi", "hdcvi"),
                Map.entry("ahd", "ahd"),
                Map.entry("tvi", "tvi")
        );

        for (Map.Entry<String, String> entry : discriminators.entrySet()) {
            if (lowerName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        String[] words = lowerName.split("\\s+");
        if (words.length > 0 && words[0].length() > 2) {
            return transliterateCyrillic(words[0])
                    .toLowerCase()
                    .replaceAll("[^a-z0-9]", "");
        }

        return null;
    }

    private String extractKeyword(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        String[] words = name.toLowerCase().split("\\s+");
        for (String word : words) {
            if (word.length() > 2) {
                String transliterated = transliterateCyrillic(word);
                return transliterated
                        .toLowerCase()
                        .replaceAll("[^a-z0-9]", "")
                        .substring(0, Math.min(4, transliterated.length()));
            }
        }

        return null;
    }

    private boolean slugExistsInMap(String slug, Map<String, Category> existingCategories) {
        return existingCategories.containsKey(slug);
    }

    private boolean slugExistsInDatabase(String slug, Category excludeParent) {
        List<Category> existing = categoryRepository.findAll().stream()
                .filter(cat -> slug.equals(cat.getSlug()))
                .toList();

        if (existing.isEmpty()) {
            return false;
        }

        if (excludeParent != null) {
            for (Category cat : existing) {
                Category catParent = cat.getParent();
                if ((catParent == null && excludeParent != null) ||
                        (catParent != null && !catParent.getId().equals(excludeParent.getId()))) {
                    return true;
                }
            }
            return false;
        }

        return true;
    }

    private Optional<Category> findExistingCategoryByTekraData(String tekraId,
                                                               String tekraSlug,
                                                               Category parentCategory) {
        List<Category> candidates = categoryRepository.findAll().stream()
                .filter(cat -> tekraId.equals(cat.getTekraId()) ||
                        tekraSlug.equals(cat.getTekraSlug()))
                .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        if (parentCategory != null) {
            for (Category candidate : candidates) {
                if (candidate.getParent() != null &&
                        candidate.getParent().getId().equals(parentCategory.getId())) {
                    return Optional.of(candidate);
                }
            }
        } else {
            for (Category candidate : candidates) {
                if (candidate.getParent() == null) {
                    return Optional.of(candidate);
                }
            }
        }

        return Optional.of(candidates.get(0));
    }

    // ============ PRODUCT CATEGORY MAPPING ============

    private Category findMostSpecificCategory(Map<String, Object> product,
                                              Map<String, Category> categoriesByName,
                                              Map<String, Category> categoriesBySlug,
                                              Map<String, Category> categoriesByTekraSlug) {
        String category3 = getStringValue(product, "category_3");
        String category2 = getStringValue(product, "category_2");
        String category1 = getStringValue(product, "category_1");
        String sourceCategorySlug = (String) product.get("source_category_slug");
        Long sourceCategoryId = (Long) product.get("source_category_id");
        String sourceCategoryParentSlug = (String) product.get("source_category_parent_slug");

        log.debug("Finding category for product: L1='{}', L2='{}', L3='{}', source='{}', sourceId={}, parentSlug='{}'",
                category1, category2, category3, sourceCategorySlug, sourceCategoryId, sourceCategoryParentSlug);

        // ПРИОРИТЕТ 1: Използваме директно source_category_id ако имаме
        // Това е НАЙ-ТОЧНОТО мачване - продуктът е извлечен от тази точна категория!
        if (sourceCategoryId != null) {
            try {
                Optional<Category> directCategory = categoryRepository.findById(sourceCategoryId);
                if (directCategory.isPresent() && isValidCategory(directCategory.get())) {
                    log.debug("✓✓✓ PERFECT MATCH using source_category_id: {} -> '{}'",
                            sourceCategoryId, directCategory.get().getNameBg());
                    return directCategory.get();
                }
            } catch (Exception e) {
                log.warn("Error finding category by source_category_id: {}", e.getMessage());
            }
        }

        // ПРИОРИТЕТ 2: Композитен ключ (parent:child) за точно мачване при дубликати
        if (sourceCategoryParentSlug != null && sourceCategorySlug != null) {
            String compositeKey = sourceCategoryParentSlug.toLowerCase() + ":" + sourceCategorySlug.toLowerCase();
            Category cat = categoriesByTekraSlug.get(compositeKey);

            if (cat != null && isValidCategory(cat)) {
                log.debug("✓✓ COMPOSITE KEY match: '{}' -> '{}'", compositeKey, cat.getNameBg());
                return cat;
            }
        }

        // ПРИОРИТЕТ 3: Опит да намерим по XML категории (само за допълнителна валидация)
        if (category3 != null && !category3.trim().isEmpty()) {
            Category cat = findCategoryByNameEnhanced(category3, categoriesByName, categoriesBySlug);
            if (cat != null && isValidCategory(cat)) {
                // Валидираме дали parent-ът съвпада
                if (category2 != null && cat.getParent() != null) {
                    if (cat.getParent().getNameBg().toLowerCase().contains(category2.toLowerCase()) ||
                            category2.toLowerCase().contains(cat.getParent().getNameBg().toLowerCase())) {
                        log.debug("✓ Mapped to L3 with parent validation: '{}' -> '{}'", category3, cat.getNameBg());
                        return cat;
                    }
                } else {
                    log.debug("✓ Mapped to L3: '{}' -> '{}'", category3, cat.getNameBg());
                    return cat;
                }
            }
        }

        if (category2 != null && !category2.trim().isEmpty()) {
            Category cat = findCategoryByNameEnhanced(category2, categoriesByName, categoriesBySlug);
            if (cat != null && isValidCategory(cat)) {
                // Валидираме дали parent-ът съвпада
                if (category1 != null && cat.getParent() != null) {
                    if (cat.getParent().getNameBg().toLowerCase().contains(category1.toLowerCase()) ||
                            category1.toLowerCase().contains(cat.getParent().getNameBg().toLowerCase())) {
                        log.debug("✓ Mapped to L2 with parent validation: '{}' -> '{}'", category2, cat.getNameBg());
                        return cat;
                    }
                } else {
                    log.debug("✓ Mapped to L2: '{}' -> '{}'", category2, cat.getNameBg());
                    return cat;
                }
            }
        }

        if (category1 != null && !category1.trim().isEmpty()) {
            Category cat = findCategoryByNameEnhanced(category1, categoriesByName, categoriesBySlug);
            if (cat != null && isValidCategory(cat)) {
                log.debug("✓ Mapped to L1: '{}' -> '{}'", category1, cat.getNameBg());
                return cat;
            }
        }

        // ПРИОРИТЕТ 4: Fallback - използваме source category само по tekra_slug (може да не е точно)
        if (sourceCategorySlug != null) {
            Category cat = categoriesByTekraSlug.get(sourceCategorySlug.toLowerCase());

            if (cat != null && isValidCategory(cat)) {
                log.debug("✓ Using source category (fallback): slug='{}' -> '{}'", sourceCategorySlug, cat.getNameBg());
                return cat;
            } else {
                log.warn("✗ Source category not found by tekra_slug: '{}'", sourceCategorySlug);
            }
        }

        log.warn("✗✗✗ NO MATCH for: L1='{}', L2='{}', L3='{}', source='{}', sourceId={}",
                category1, category2, category3, sourceCategorySlug, sourceCategoryId);
        return null;
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
}