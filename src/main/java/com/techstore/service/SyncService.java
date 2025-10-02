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

            // СТЪПКА 3: Обработи level-3 категории
            log.info("=== STEP 3: Processing level-3 categories ===");

            int totalLevel3 = 0;
            for (int i = 0; i < subCategories.size(); i++) {
                Map<String, Object> subCat = subCategories.get(i);

                try {
                    String subCatSlug = getString(subCat, "slug");
                    String subCatName = getString(subCat, "name");

                    Category parentCategory = level2Categories.get(subCatSlug);

                    if (parentCategory == null) {
                        log.warn("✗ Parent category NOT FOUND for slug: '{}'", subCatSlug);
                        continue;
                    }

                    Object subSubCatObj = subCat.get("subsubcat");
                    if (!(subSubCatObj instanceof List)) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> subSubCategories = (List<Map<String, Object>>) subSubCatObj;

                    for (int j = 0; j < subSubCategories.size(); j++) {
                        Map<String, Object> subSubCat = subSubCategories.get(j);

                        try {
                            String subSubCatSlug = getString(subSubCat, "slug");
                            String subSubCatName = getString(subSubCat, "name");

                            if (subSubCatSlug == null || subSubCatName == null) {
                                skipped++;
                                continue;
                            }

                            if (parentCategory.getId() == null) {
                                skipped++;
                                continue;
                            }

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
                            }

                        } catch (Exception e) {
                            log.error("  ERROR processing level-3 category: {}", e.getMessage(), e);
                            skipped++;
                        }
                    }

                } catch (Exception e) {
                    log.error("ERROR processing subcategories: {}", e.getMessage(), e);
                    skipped++;
                }
            }

            log.info("=== STEP 3 COMPLETE: {} level-3 categories processed ===", totalLevel3);

            entityManager.flush();
            entityManager.clear();

            long totalCategories = created + updated;
            updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, totalCategories, created, updated, skipped,
                    skipped > 0 ? String.format("Skipped %d categories", skipped) : null, startTime);

            log.info("Tekra categories sync completed - Total: {}, Created: {}, Updated: {}, Skipped: {}",
                    totalCategories, created, updated, skipped);

        } catch (Exception e) {
            log.error("=== SYNC FAILED ===", e);
            updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, e.getMessage(), startTime);
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

            Set<String> allTekraManufacturers = new HashSet<>();

            for (Category category : tekraCategories) {
                try {
                    Set<String> categoryManufacturers = tekraApiService
                            .extractTekraManufacturersFromProducts(category.getTekraSlug());
                    allTekraManufacturers.addAll(categoryManufacturers);
                } catch (Exception e) {
                    log.error("Error extracting manufacturers: {}", e.getMessage());
                }
            }

            if (allTekraManufacturers.isEmpty()) {
                updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, 0, 0, 0, 0, "No manufacturers found", startTime);
                return;
            }

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
                        } else {
                            errors++;
                        }
                    } else {
                        updated++;
                    }
                } catch (Exception e) {
                    errors++;
                    log.error("Error processing manufacturer {}: {}", manufacturerName, e.getMessage());
                }
            }

            updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, (long) allTekraManufacturers.size(),
                    created, updated, errors,
                    errors > 0 ? String.format("Completed with %d errors", errors) : null, startTime);

        } catch (Exception e) {
            updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, e.getMessage(), startTime);
            throw e;
        }
    }

    @Transactional
    public void syncTekraParameters() {
        SyncLog syncLog = createSyncLogSimple("TEKRA_PARAMETERS");
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting Tekra parameters synchronization - FIXED VERSION");

            List<Category> tekraCategories = categoryRepository.findAll().stream()
                    .filter(cat -> cat.getTekraSlug() != null)
                    .toList();

            if (tekraCategories.isEmpty()) {
                log.error("No Tekra categories found");
                updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, "No Tekra categories found", startTime);
                return;
            }

            log.info("Found {} Tekra categories", tekraCategories.size());

            // Подготовка на category maps за бързо търсене
            Map<String, Category> categoriesByName = new HashMap<>();
            Map<String, Category> categoriesBySlug = new HashMap<>();
            Map<String, Category> categoriesByTekraSlug = new HashMap<>();

            for (Category cat : tekraCategories) {
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

            // ✅ Step 1: Събираме ВСИЧКИ продукти и мапваме ги към правилната категория
            log.info("Step 1: Collecting all products and mapping to correct categories...");
            Map<Long, Map<String, Set<String>>> categorizedParameters = new HashMap<>();
            Set<String> processedSkus = new HashSet<>();
            int totalProducts = 0;
            int productsWithCategory = 0;
            int productsWithoutCategory = 0;

            Map<String, Integer> matchTypeStats = new HashMap<>();
            matchTypeStats.put("perfect_path", 0);
            matchTypeStats.put("partial_path", 0);
            matchTypeStats.put("name_match", 0);
            matchTypeStats.put("no_match", 0);

            for (Category category : tekraCategories) {
                try {
                    List<Map<String, Object>> products = tekraApiService.getProductsRaw(category.getTekraSlug());

                    for (Map<String, Object> product : products) {
                        String sku = getStringValue(product, "sku");
                        if (sku != null && !processedSkus.contains(sku)) {
                            processedSkus.add(sku);
                            totalProducts++;

                            // ✅ КРИТИЧНО: Използваме СЪЩАТА логика като в syncTekraProducts
                            Category productCategory = findMostSpecificCategory(product,
                                    categoriesByName, categoriesBySlug, categoriesByTekraSlug, matchTypeStats);

                            if (productCategory != null && isValidCategory(productCategory)) {
                                // ✅ ВАЖНО: Игнорираме root категории (без parent)
                                if (productCategory.getParent() == null) {
                                    log.debug("Skipping root category '{}' for product {}",
                                            productCategory.getNameBg(), sku);
                                    continue;
                                }

                                productsWithCategory++;
                                Long catId = productCategory.getId();

                                // Извлечи параметрите от продукта
                                Map<String, String> productParams = extractTekraParameters(product);

                                if (!productParams.isEmpty()) {
                                    // Групирай по категория
                                    categorizedParameters.putIfAbsent(catId, new HashMap<>());
                                    Map<String, Set<String>> categoryParams = categorizedParameters.get(catId);

                                    for (Map.Entry<String, String> param : productParams.entrySet()) {
                                        categoryParams.putIfAbsent(param.getKey(), new HashSet<>());
                                        categoryParams.get(param.getKey()).add(param.getValue());
                                    }

                                    log.debug("Product {} → Category '{}' (path: '{}') with {} params",
                                            sku, productCategory.getNameBg(), productCategory.getCategoryPath(),
                                            productParams.size());
                                }
                            } else {
                                productsWithoutCategory++;
                                log.debug("Product {} has NO category match", sku);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Error processing category {}: {}", category.getNameBg(), e.getMessage());
                }
            }

            log.info("Step 1 Complete:");
            log.info("  Total products: {}", totalProducts);
            log.info("  Products with category: {}", productsWithCategory);
            log.info("  Products without category: {}", productsWithoutCategory);
            log.info("  Categories with parameters: {}", categorizedParameters.size());

            log.info("  Category matching stats:");
            matchTypeStats.forEach((type, count) -> log.info("    {}: {}", type, count));

            if (categorizedParameters.isEmpty()) {
                log.error("No parameters found for any category!");
                updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0,
                        "No parameters found", startTime);
                return;
            }

            // ✅ Step 2: Синхронизирай параметрите за всяка категория
            log.info("Step 2: Syncing parameters to database...");

            long totalProcessed = 0, totalCreated = 0, totalUpdated = 0, totalErrors = 0;
            long totalParameterOptionsCreated = 0, totalParameterOptionsUpdated = 0;

            for (Map.Entry<Long, Map<String, Set<String>>> catEntry : categorizedParameters.entrySet()) {
                try {
                    Long categoryId = catEntry.getKey();
                    Optional<Category> categoryOpt = categoryRepository.findById(categoryId);

                    if (categoryOpt.isEmpty()) {
                        log.warn("Category with ID {} not found", categoryId);
                        continue;
                    }

                    Category category = categoryOpt.get();
                    Map<String, Set<String>> parametersMap = catEntry.getValue();

                    log.info("Processing category '{}' (path: '{}') with {} parameter types",
                            category.getNameBg(), category.getCategoryPath(), parametersMap.size());

                    Map<String, Parameter> existingParameters = parameterRepository.findByCategoryId(category.getId())
                            .stream()
                            .collect(Collectors.toMap(
                                    p -> p.getTekraKey() != null ? p.getTekraKey() : p.getNameBg(),
                                    p -> p,
                                    (existing, duplicate) -> existing
                            ));

                    long categoryParamsCreated = 0, categoryParamsUpdated = 0, categoryParamsErrors = 0;
                    long categoryOptionsCreated = 0, categoryOptionsUpdated = 0;

                    for (Map.Entry<String, Set<String>> paramEntry : parametersMap.entrySet()) {
                        try {
                            String parameterKey = paramEntry.getKey();
                            Set<String> parameterValues = paramEntry.getValue();

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
                            } else {
                                categoryParamsUpdated++;
                            }

                            // Sync options
                            Map<String, ParameterOption> existingOptions = parameterOptionRepository
                                    .findByParameterIdOrderByOrderAsc(parameter.getId())
                                    .stream()
                                    .collect(Collectors.toMap(ParameterOption::getNameBg, o -> o));

                            int orderCounter = existingOptions.size();
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
                                    } else {
                                        categoryOptionsUpdated++;
                                    }
                                } catch (Exception e) {
                                    categoryParamsErrors++;
                                    log.error("Error processing parameter option '{}': {}",
                                            optionValue, e.getMessage());
                                }
                            }

                            totalProcessed++;

                        } catch (Exception e) {
                            categoryParamsErrors++;
                            log.error("Error processing parameter '{}': {}",
                                    paramEntry.getKey(), e.getMessage());
                        }
                    }

                    totalCreated += categoryParamsCreated;
                    totalUpdated += categoryParamsUpdated;
                    totalErrors += categoryParamsErrors;
                    totalParameterOptionsCreated += categoryOptionsCreated;
                    totalParameterOptionsUpdated += categoryOptionsUpdated;

                    log.info("✓ Category '{}': {} params ({} new, {} updated), {} options ({} new, {} updated)",
                            category.getNameBg(),
                            categoryParamsCreated + categoryParamsUpdated,
                            categoryParamsCreated, categoryParamsUpdated,
                            categoryOptionsCreated + categoryOptionsUpdated,
                            categoryOptionsCreated, categoryOptionsUpdated);

                } catch (Exception e) {
                    totalErrors++;
                    log.error("Error syncing parameters for category ID {}: {}",
                            catEntry.getKey(), e.getMessage(), e);
                }
            }

            String message = String.format("Parameters: %d created, %d updated. Options: %d created, %d updated",
                    totalCreated, totalUpdated, totalParameterOptionsCreated, totalParameterOptionsUpdated);
            if (totalErrors > 0) {
                message += String.format(". %d errors occurred", totalErrors);
            }

            updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, totalProcessed, totalCreated, totalUpdated,
                    totalErrors, message, startTime);

            log.info("=== Tekra parameters sync complete! ===");
            log.info("Total: {} params, {} options", totalCreated + totalUpdated,
                    totalParameterOptionsCreated + totalParameterOptionsUpdated);

        } catch (Exception e) {
            updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, e.getMessage(), startTime);
            log.error("Tekra parameters sync failed", e);
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
                    List<Map<String, Object>> categoryProducts = tekraApiService.getProductsRaw(categorySlug);

                    for (Map<String, Object> product : categoryProducts) {
                        String sku = getStringValue(product, "sku");
                        if (sku != null && !processedSkus.contains(sku)) {
                            allProducts.add(product);
                            processedSkus.add(sku);
                        }
                    }

                } catch (Exception e) {
                    log.error("Error fetching products for category: {}", e.getMessage());
                }
            }

            log.info("STEP 1 COMPLETE: Collected {} unique products from {} categories",
                    allProducts.size(), allCategories.size());

            // ✅ DEBUG: Покажи sample продукти
            log.info("Sample products:");
            allProducts.stream().limit(5).forEach(p ->
                    log.info("  - SKU: {}, Name: {}, L1={}, L2={}, L3={}",
                            getStringValue(p, "sku"),
                            getStringValue(p, "name"),
                            getStringValue(p, "category_1"),
                            getStringValue(p, "category_2"),
                            getStringValue(p, "category_3")
                    )
            );

            if (allProducts.isEmpty()) {
                log.warn("No products found in any category");
                updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, 0, 0, 0, 0, "No products found", startTime);
                return;
            }

            // STEP 2: Prepare category maps
            log.info("STEP 2: Loading categories for matching...");
            Map<String, Category> categoriesByPath = new HashMap<>();
            Map<String, Category> categoriesByName = new HashMap<>();
            Map<String, Category> categoriesBySlug = new HashMap<>();
            Map<String, Category> categoriesByTekraSlug = new HashMap<>();

            for (Category cat : allCategories) {
                if (cat.getCategoryPath() != null) {
                    categoriesByPath.put(cat.getCategoryPath().toLowerCase(), cat);
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
            log.info("STEP 3: Processing {} products...", allProducts.size());

            long totalProcessed = 0, totalCreated = 0, totalUpdated = 0, totalErrors = 0;
            long skippedNoCategory = 0;

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

                    // ✅ Намираме правилната категория
                    Category productCategory = findMostSpecificCategory(rawProduct,
                            categoriesByName, categoriesBySlug, categoriesByTekraSlug, matchTypeStats);

                    if (productCategory == null || !isValidCategory(productCategory)) {
                        log.warn("✗✗✗ Skipping product '{}' ({}): NO VALID CATEGORY", name, sku);
                        skippedNoCategory++;
                        matchTypeStats.put("no_match", matchTypeStats.get("no_match") + 1);
                        continue;
                    }

                    log.info("✓✓✓ Product '{}' → category: '{}' (path: '{}')",
                            sku, productCategory.getNameBg(), productCategory.getCategoryPath());

                    // ✅ Намираме/създаваме продукта С категорията
                    Product product = findOrCreateProduct(sku, rawProduct, productCategory);

                    // ✅ ВАЖНА ПРОВЕРКА: Гарантираме че категорията е зададена
                    if (product.getCategory() == null) {
                        log.error("Product {} still has no category after findOrCreateProduct!", sku);
                        product.setCategory(productCategory);
                    }

                    boolean isNew = (product.getId() == null);

                    // ✅ Запазваме продукта ПРЕДИ параметрите
                    product = productRepository.save(product);

                    if (isNew) {
                        totalCreated++;
                    } else {
                        totalUpdated++;
                    }

                    // ✅ Сега мапваме параметрите (продуктът има категория!)
                    if (product.getCategory() != null) {
                        setTekraParametersToProduct(product, rawProduct);
                        product = productRepository.save(product);
                    } else {
                        log.error("Cannot set parameters for product {} - no category!", sku);
                    }

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

    // ============ CATEGORY MATCHING WITH FALLBACK ============

    /**
     * ✅ ПОДОБРЕНА ВЕРСИЯ: Намира най-специфичната категория с множество fallback стратегии
     */
    private Category findMostSpecificCategory(Map<String, Object> product,
                                              Map<String, Category> categoriesByName,
                                              Map<String, Category> categoriesBySlug,
                                              Map<String, Category> categoriesByTekraSlug,
                                              Map<String, Integer> matchTypeStats) {  // ✅ Добави параметър
        String category3 = getStringValue(product, "category_3");
        String category2 = getStringValue(product, "category_2");
        String category1 = getStringValue(product, "category_1");
        String sku = getStringValue(product, "sku");

        String expectedPath = buildCategoryPath(category1, category2, category3);

        if (expectedPath == null) {
            log.warn("Product {}: Cannot build path (all categories null)", sku);
            matchTypeStats.put("no_match", matchTypeStats.get("no_match") + 1);
            return null;
        }

        log.debug("Product {}: Expected path '{}'", sku, expectedPath);

        // СТРАТЕГИЯ 1: Точно съвпадение на пълния път
        Optional<Category> exactMatch = categoryRepository.findAll().stream()
                .filter(cat -> cat.getCategoryPath() != null)
                .filter(cat -> expectedPath.equalsIgnoreCase(cat.getCategoryPath()))
                .findFirst();

        if (exactMatch.isPresent() && isValidCategory(exactMatch.get())) {
            log.info("✓✓✓ EXACT PATH: {} -> '{}'", sku, exactMatch.get().getNameBg());
            matchTypeStats.put("perfect_path", matchTypeStats.get("perfect_path") + 1);  // ✅
            return exactMatch.get();
        }

        // СТРАТЕГИЯ 2: Частично съвпадение (L1+L2)
        if (category2 != null) {
            String partialPath = buildCategoryPath(category1, category2, null);

            Optional<Category> partialMatch = categoryRepository.findAll().stream()
                    .filter(cat -> cat.getCategoryPath() != null)
                    .filter(cat -> partialPath.equalsIgnoreCase(cat.getCategoryPath()))
                    .findFirst();

            if (partialMatch.isPresent() && isValidCategory(partialMatch.get())) {
                log.info("✓✓ PARTIAL PATH (L1+L2): {} -> '{}' | L3 '{}' not found",
                        sku, partialMatch.get().getNameBg(), category3);
                matchTypeStats.put("partial_path", matchTypeStats.get("partial_path") + 1);  // ✅
                return partialMatch.get();
            }
        }

        // СТРАТЕГИЯ 3: Само L1
        if (category1 != null) {
            String l1Path = buildCategoryPath(category1, null, null);

            Optional<Category> l1Match = categoryRepository.findAll().stream()
                    .filter(cat -> cat.getCategoryPath() != null)
                    .filter(cat -> l1Path.equalsIgnoreCase(cat.getCategoryPath()))
                    .findFirst();

            if (l1Match.isPresent() && isValidCategory(l1Match.get())) {
                log.info("✓ L1 FALLBACK: {} -> '{}'", sku, l1Match.get().getNameBg());
                matchTypeStats.put("partial_path", matchTypeStats.get("partial_path") + 1);  // ✅
                return l1Match.get();
            }
        }

        // СТРАТЕГИЯ 4: tekraSlug
        if (category3 != null) {
            String normalizedCat3 = normalizeCategoryForPath(category3);
            Optional<Category> match = categoryRepository.findAll().stream()
                    .filter(cat -> normalizedCat3.equalsIgnoreCase(cat.getTekraSlug()))
                    .filter(this::isValidCategory)
                    .findFirst();

            if (match.isPresent()) {
                log.info("✓ TEKRA SLUG (L3): {} -> '{}'", sku, match.get().getNameBg());
                matchTypeStats.put("name_match", matchTypeStats.get("name_match") + 1);  // ✅
                return match.get();
            }
        }

        if (category2 != null) {
            String normalizedCat2 = normalizeCategoryForPath(category2);
            Optional<Category> match = categoryRepository.findAll().stream()
                    .filter(cat -> normalizedCat2.equalsIgnoreCase(cat.getTekraSlug()))
                    .filter(this::isValidCategory)
                    .findFirst();

            if (match.isPresent()) {
                log.info("✓ TEKRA SLUG (L2): {} -> '{}'", sku, match.get().getNameBg());
                matchTypeStats.put("name_match", matchTypeStats.get("name_match") + 1);  // ✅
                return match.get();
            }
        }

        // СТРАТЕГИЯ 5: име
        if (category3 != null) {
            Optional<Category> match = categoryRepository.findAll().stream()
                    .filter(cat -> category3.equalsIgnoreCase(cat.getNameBg()))
                    .filter(this::isValidCategory)
                    .findFirst();

            if (match.isPresent()) {
                log.info("✓ NAME (L3): {} -> '{}'", sku, match.get().getNameBg());
                matchTypeStats.put("name_match", matchTypeStats.get("name_match") + 1);  // ✅
                return match.get();
            }
        }

        if (category2 != null) {
            Optional<Category> match = categoryRepository.findAll().stream()
                    .filter(cat -> category2.equalsIgnoreCase(cat.getNameBg()))
                    .filter(this::isValidCategory)
                    .findFirst();

            if (match.isPresent()) {
                log.info("✓ NAME (L2): {} -> '{}'", sku, match.get().getNameBg());
                matchTypeStats.put("name_match", matchTypeStats.get("name_match") + 1);  // ✅
                return match.get();
            }
        }

        if (category1 != null) {
            Optional<Category> match = categoryRepository.findAll().stream()
                    .filter(cat -> category1.equalsIgnoreCase(cat.getNameBg()))
                    .filter(this::isValidCategory)
                    .findFirst();

            if (match.isPresent()) {
                log.info("✓ NAME (L1): {} -> '{}'", sku, match.get().getNameBg());
                matchTypeStats.put("name_match", matchTypeStats.get("name_match") + 1);  // ✅
                return match.get();
            }
        }

        log.warn("✗✗✗ NO MATCH: {} | Path: '{}'", sku, expectedPath);
        matchTypeStats.put("no_match", matchTypeStats.get("no_match") + 1);  // ✅

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

    private String normalizeCategoryForPath(String categoryName) {
        if (categoryName == null) return null;

        String transliterated = transliterateCyrillic(categoryName.trim());

        return transliterated.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    // ============ PRODUCT OPERATIONS ============

    /**
     * ✅ КОРИГИРАН: Задава категорията ПРЕДИ updateFields
     */
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
            } else {
                product = new Product();
                product.setSku(sku);
            }

            // ✅ ВАЖНО: Задаваме категорията ПРЕДИ updateFields
            product.setCategory(category);

            // Обновяваме полетата (БЕЗ setCategoryFromTekraXML!)
            updateProductFieldsFromTekraXML(product, rawProduct, category.getTekraSlug());

            return product;

        } catch (Exception e) {
            log.error("Error in findOrCreateProduct for SKU {}: {}", sku, e.getMessage());
            throw e;
        }
    }

    /**
     * ✅ КОРИГИРАН: БЕЗ извикване на setCategoryFromTekraXML
     */
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

            // ✅ КАТЕГОРИЯТА СЕ ЗАДАВА В findOrCreateProduct, не тук!
            // Само ако по някаква причина няма категория, опитваме fallback
            if (product.getCategory() == null) {
                log.warn("Product {} has no category set, trying fallback", product.getSku());
                String cat1 = getStringValue(rawData, "category_1");
                String cat2 = getStringValue(rawData, "category_2");
                String cat3 = getStringValue(rawData, "category_3");

                if (cat3 != null) {
                    categoryRepository.findByNameBg(cat3).ifPresent(product::setCategory);
                } else if (cat2 != null) {
                    categoryRepository.findByNameBg(cat2).ifPresent(product::setCategory);
                } else if (cat1 != null) {
                    categoryRepository.findByNameBg(cat1).ifPresent(product::setCategory);
                }
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

    private void setManufacturerFromName(Product product, String manufacturerName) {
        manufacturerRepository.findByName(manufacturerName)
                .or(() -> {
                    Manufacturer manufacturer = new Manufacturer();
                    manufacturer.setName(manufacturerName);
                    return Optional.of(manufacturerRepository.save(manufacturer));
                })
                .ifPresent(product::setManufacturer);
    }

    // ============ PARAMETER MAPPING ============

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

        // ✅ ДИНАМИЧНО: Извлечи ВСИЧКИ полета започващи с "prop_"
        for (Map.Entry<String, Object> entry : rawProduct.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (key != null && key.startsWith("prop_") && value != null) {
                String paramKey = key.substring(5); // Премахни "prop_" префикса
                String paramValue = value.toString().trim();

                if (!paramValue.isEmpty()) {
                    parameters.put(paramKey, paramValue);
                    log.trace("Found parameter: {} = {}", paramKey, paramValue);
                }
            }
        }

        // Fallback: Опит за директни полета (без "prop_")
        if (parameters.isEmpty()) {
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

    // ============ HELPER METHODS FOR STRUCTURAL CATEGORIES ============

    private Category createOrUpdateTekraCategory(Map<String, Object> rawData,
                                                 Map<String, Category> existingCategories,
                                                 Category parentCategory) {
        try {
            String tekraId = getString(rawData, "id");
            String tekraSlug = getString(rawData, "slug");
            String name = getString(rawData, "name");

            if (tekraId == null || tekraSlug == null || name == null) {
                log.warn("Cannot create category with missing fields");
                return null;
            }

            Optional<Category> existingCategoryOpt = findExistingCategoryByTekraData(
                    tekraId, tekraSlug, parentCategory);

            Category category;
            boolean isNew = false;

            if (existingCategoryOpt.isPresent()) {
                category = existingCategoryOpt.get();

                boolean parentMatches = false;
                if (parentCategory == null && category.getParent() == null) {
                    parentMatches = true;
                } else if (parentCategory != null && category.getParent() != null &&
                        parentCategory.getId().equals(category.getParent().getId())) {
                    parentMatches = true;
                }

                if (!parentMatches) {
                    log.warn("Found category but WRONG parent! Creating NEW category.");
                    category = new Category();
                    category.setTekraId(tekraId);
                    isNew = true;
                }
            } else {
                category = new Category();
                category.setTekraId(tekraId);
                isNew = true;
            }

            category.setTekraSlug(tekraSlug);
            category.setNameBg(name);
            category.setNameEn(name);
            category.setParent(parentCategory);

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

            String uniqueSlug = generateUniqueSlug(tekraSlug, name, parentCategory, existingCategories);
            category.setSlug(uniqueSlug);

            category.setCategoryPath(category.generateCategoryPath());

            category = categoryRepository.save(category);
            categoryRepository.flush();

            if (isNew) {
                log.info("✓ CREATED: '{}' | slug='{}' | path='{}'",
                        name, uniqueSlug, category.getCategoryPath());
            } else {
                log.info("✓ UPDATED: '{}' | slug='{}' | path='{}'",
                        name, uniqueSlug, category.getCategoryPath());
            }

            return category;

        } catch (Exception e) {
            log.error("Error creating/updating category: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create/update category", e);
        }
    }

    private String generateUniqueSlug(String tekraSlug, String categoryName,
                                      Category parentCategory,
                                      Map<String, Category> existingCategories) {
        if (tekraSlug == null || tekraSlug.isEmpty()) {
            tekraSlug = createSlugFromName(categoryName);
        }

        String baseSlug = tekraSlug;

        if (parentCategory == null) {
            if (!slugExistsInMap(baseSlug, existingCategories) &&
                    !slugExistsInDatabase(baseSlug, null)) {
                return baseSlug;
            }
            return baseSlug + "-root";
        }

        String parentSlug = parentCategory.getSlug();
        if (parentSlug == null || parentSlug.isEmpty()) {
            parentSlug = parentCategory.getTekraSlug();
            if (parentSlug == null) {
                parentSlug = "cat-" + parentCategory.getId();
            }
        }

        String hierarchicalSlug = parentSlug + "-" + baseSlug;

        if (!slugExistsInMap(hierarchicalSlug, existingCategories) &&
                !slugExistsInDatabase(hierarchicalSlug, parentCategory)) {
            return hierarchicalSlug;
        }

        Category existing = existingCategories.get(hierarchicalSlug);
        if (existing != null && existing.getParent() != null && parentCategory != null &&
                existing.getParent().getId().equals(parentCategory.getId())) {
            return hierarchicalSlug;
        }

        String discriminator = extractDiscriminator(categoryName);
        if (discriminator != null && !discriminator.isEmpty()) {
            String discriminatedSlug = hierarchicalSlug + "-" + discriminator;
            if (!slugExistsInMap(discriminatedSlug, existingCategories) &&
                    !slugExistsInDatabase(discriminatedSlug, parentCategory)) {
                return discriminatedSlug;
            }
        }

        int counter = 2;
        String numberedSlug;
        do {
            numberedSlug = hierarchicalSlug + "-" + counter;
            counter++;
        } while ((slugExistsInMap(numberedSlug, existingCategories) ||
                slugExistsInDatabase(numberedSlug, parentCategory)) && counter < 100);

        return numberedSlug;
    }

    private boolean slugExistsInMap(String slug, Map<String, Category> existingCategories) {
        return existingCategories.containsKey(slug);
    }

    private boolean slugExistsInDatabase(String slug, Category parentCategory) {
        List<Category> existing = categoryRepository.findAll().stream()
                .filter(cat -> slug.equals(cat.getSlug()))
                .toList();

        if (existing.isEmpty()) {
            return false;
        }

        if (parentCategory != null) {
            for (Category cat : existing) {
                Category catParent = cat.getParent();

                if (catParent == null && parentCategory != null) {
                    return true;
                }
                if (catParent != null && parentCategory == null) {
                    return true;
                }
                if (catParent != null && !catParent.getId().equals(parentCategory.getId())) {
                    return true;
                }
            }
            return false;
        }

        return true;
    }

    private String extractDiscriminator(String categoryName) {
        if (categoryName == null || categoryName.isEmpty()) {
            return null;
        }

        String lowerName = categoryName.toLowerCase();

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

        for (Map.Entry<String, String> entry : keywords.entrySet()) {
            if (lowerName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

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

    private Optional<Category> findExistingCategoryByTekraData(String tekraId,
                                                               String tekraSlug,
                                                               Category parentCategory) {
        if (tekraId != null) {
            List<Category> byTekraId = categoryRepository.findAll().stream()
                    .filter(cat -> tekraId.equals(cat.getTekraId()))
                    .toList();

            if (!byTekraId.isEmpty()) {
                for (Category cat : byTekraId) {
                    boolean parentMatches = false;

                    if (parentCategory == null && cat.getParent() == null) {
                        parentMatches = true;
                    } else if (parentCategory != null && cat.getParent() != null &&
                            parentCategory.getId().equals(cat.getParent().getId())) {
                        parentMatches = true;
                    }

                    if (parentMatches) {
                        return Optional.of(cat);
                    }
                }
                return Optional.empty();
            }
        }

        if (tekraSlug != null) {
            List<Category> byTekraSlug = categoryRepository.findAll().stream()
                    .filter(cat -> tekraSlug.equals(cat.getTekraSlug()))
                    .toList();

            if (!byTekraSlug.isEmpty()) {
                for (Category cat : byTekraSlug) {
                    boolean parentMatches = false;

                    if (parentCategory == null && cat.getParent() == null) {
                        parentMatches = true;
                    } else if (parentCategory != null && cat.getParent() != null &&
                            parentCategory.getId().equals(cat.getParent().getId())) {
                        parentMatches = true;
                    }

                    if (parentMatches) {
                        return Optional.of(cat);
                    }
                }
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    // ============ VALIDATION & UTILITY ============

    private boolean isValidCategory(Category category) {
        if (category == null) {
            return false;
        }
        if (category.getId() == null) {
            return false;
        }
        if (category.getNameBg() == null || category.getNameBg().trim().isEmpty()) {
            return false;
        }
        return true;
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

                List<Product> products = productRepository.findProductsBySkuCode(sku);
                if (products.size() > 1) {
                    Product productToKeep = products.get(0);
                    for (int i = 1; i < products.size(); i++) {
                        productRepository.delete(products.get(i));
                    }
                }
            }
        }

        List<Object[]> duplicateExternalIds = productRepository.findDuplicateProductsByExternalId();
        if (!duplicateExternalIds.isEmpty()) {
            for (Object[] duplicate : duplicateExternalIds) {
                Long externalId = (Long) duplicate[0];

                List<Product> products = productRepository.findProductsByExternalId(externalId);
                if (products.size() > 1) {
                    for (int i = 1; i < products.size(); i++) {
                        productRepository.delete(products.get(i));
                    }
                }
            }
        }
    }

    // ============ VALI API HELPER METHODS ============

    private void syncValiParameterOptions(Parameter parameter, List<ParameterOptionRequestDto> externalOptions) {
        if (externalOptions == null || externalOptions.isEmpty()) {
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
            log.error("Error creating Vali parameter option: {}", e.getMessage());
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
                log.error("Error mapping parameter: {}", e.getMessage());
                notFoundCount++;
            }
        }

        product.setProductParameters(newProductParameters);
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
            log.error("Error creating manufacturer: {}", e.getMessage());
            return null;
        }
    }

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

        boolean nameChanged = !category.getNameBg().equals(oldNameBg) ||
                (category.getNameEn() != null && !category.getNameEn().equals(oldNameEn));

        if (category.getSlug() == null || category.getSlug().isEmpty() || nameChanged) {
            String baseName = category.getNameEn() != null ? category.getNameEn() : category.getNameBg();
            category.setSlug(generateUniqueSlugForVali(baseName, category));
        }
    }

    private String generateUniqueSlugForVali(String categoryName, Category category) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return "category-" + System.currentTimeMillis();
        }

        String baseSlug = createSlugFromName(categoryName);

        if (!slugExistsInDatabaseForVali(baseSlug, category.getId())) {
            return baseSlug;
        }

        String discriminator = extractDiscriminator(categoryName);
        if (discriminator != null && !discriminator.isEmpty()) {
            String discriminatedSlug = baseSlug + "-" + discriminator;
            if (!slugExistsInDatabaseForVali(discriminatedSlug, category.getId())) {
                return discriminatedSlug;
            }
        }

        int counter = 1;
        String numberedSlug;
        do {
            numberedSlug = baseSlug + "-" + counter;
            counter++;
        } while (slugExistsInDatabaseForVali(numberedSlug, category.getId()));

        return numberedSlug;
    }

    private boolean slugExistsInDatabaseForVali(String slug, Long excludeId) {
        List<Category> existing = categoryRepository.findAll().stream()
                .filter(cat -> slug.equals(cat.getSlug()))
                .toList();

        if (existing.isEmpty()) {
            return false;
        }

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
                    log.error("Error processing product chunk: {}", e.getMessage());
                    errors += chunk.size();
                }
            }

        } catch (Exception e) {
            log.error("Error getting products for category: {}", e.getMessage());
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
                    break;
                }

            } catch (Exception e) {
                errors++;
                log.error("Error processing product: {}", e.getMessage());
            }
        }

        entityManager.flush();
        entityManager.clear();

        return new ChunkResult(processed, created, updated, errors);
    }

    private void createProductFromExternal(ProductRequestDto extProduct, Map<Long, Manufacturer> manufacturersMap) {
        Manufacturer manufacturer = manufacturersMap.get(extProduct.getManufacturerId());

        Product product = new Product();
        product.setId(null);
        updateProductFieldsFromExternal(product, extProduct, manufacturer);

        try {
            productRepository.save(product);
        } catch (Exception e) {
            log.error("Failed to create product: {}", e.getMessage());
            throw e;
        }
    }

    private void updateProductFromExternal(Product product, ProductRequestDto extProduct, Map<Long, Manufacturer> manufacturersMap) {
        Manufacturer manufacturer = manufacturersMap.get(extProduct.getManufacturerId());
        updateProductFieldsFromExternal(product, extProduct, manufacturer);

        try {
            productRepository.save(product);
        } catch (Exception e) {
            log.error("Failed to update product: {}", e.getMessage());
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
                if ("bg".equals(name.getLanguageCode())) {
                    product.setNameBg(name.getText());
                } else if ("en".equals(name.getLanguageCode())) {
                    product.setNameEn(name.getText());
                }
            });
        }
    }

    private static void setDescriptionToProduct(Product product, ProductRequestDto extProduct) {
        if (extProduct.getDescription() != null) {
            extProduct.getDescription().forEach(desc -> {
                if ("bg".equals(desc.getLanguageCode())) {
                    product.setDescriptionBg(desc.getText());
                } else if ("en".equals(desc.getLanguageCode())) {
                    product.setDescriptionEn(desc.getText());
                }
            });
        }
    }

    // ============ SYNC LOG METHODS ============

    private SyncLog createSyncLogSimple(String syncType) {
        SyncLog syncLog = new SyncLog();
        syncLog.setSyncType(syncType);
        syncLog.setStatus(LOG_STATUS_IN_PROGRESS);
        return syncLogRepository.save(syncLog);
    }

    private void updateSyncLogSimple(SyncLog syncLog, String status, long totalRecords,
                                     long created, long updated, long errors,
                                     String message, long startTime) {
        syncLog.setStatus(status);
        syncLog.setRecordsProcessed(totalRecords);
        syncLog.setRecordsCreated(created);
        syncLog.setRecordsUpdated(updated);
        syncLog.setErrorMessage(message);
        syncLog.setCreatedAt(java.time.LocalDateTime.now());
        syncLog.setDurationMs(System.currentTimeMillis() - startTime);
        syncLogRepository.save(syncLog);
    }

    // ============ UTILITY METHODS ============

    private String createSlugFromName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        return transliterateCyrillic(name)
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    private String transliterateCyrillic(String text) {
        if (text == null) return "";

        Map<Character, String> transliterationMap = Map.ofEntries(
                Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"),
                Map.entry('г', "g"), Map.entry('д', "d"), Map.entry('е', "e"),
                Map.entry('ж', "zh"), Map.entry('з', "z"), Map.entry('и', "i"),
                Map.entry('й', "y"), Map.entry('к', "k"), Map.entry('л', "l"),
                Map.entry('м', "m"), Map.entry('н', "n"), Map.entry('о', "o"),
                Map.entry('п', "p"), Map.entry('р', "r"), Map.entry('с', "s"),
                Map.entry('т', "t"), Map.entry('у', "u"), Map.entry('ф', "f"),
                Map.entry('х', "h"), Map.entry('ц', "ts"), Map.entry('ч', "ch"),
                Map.entry('ш', "sh"), Map.entry('щ', "sht"), Map.entry('ъ', "a"),
                Map.entry('ь', "y"), Map.entry('ю', "yu"), Map.entry('я', "ya"),
                Map.entry('А', "A"), Map.entry('Б', "B"), Map.entry('В', "V"),
                Map.entry('Г', "G"), Map.entry('Д', "D"), Map.entry('Е', "E"),
                Map.entry('Ж', "Zh"), Map.entry('З', "Z"), Map.entry('И', "I"),
                Map.entry('Й', "Y"), Map.entry('К', "K"), Map.entry('Л', "L"),
                Map.entry('М', "M"), Map.entry('Н', "N"), Map.entry('О', "O"),
                Map.entry('П', "P"), Map.entry('Р', "R"), Map.entry('С', "S"),
                Map.entry('Т', "T"), Map.entry('У', "U"), Map.entry('Ф', "F"),
                Map.entry('Х', "H"), Map.entry('Ц', "Ts"), Map.entry('Ч', "Ch"),
                Map.entry('Ш', "Sh"), Map.entry('Щ', "Sht"), Map.entry('Ъ', "A"),
                Map.entry('Ь', "Y"), Map.entry('Ю', "Yu"), Map.entry('Я', "Ya")
        );

        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            result.append(transliterationMap.getOrDefault(c, String.valueOf(c)));
        }
        return result.toString();
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return value.toString().trim();
    }

    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer getIntegerValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private <T> List<List<T>> partitionList(List<T> list, int partitionSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += partitionSize) {
            partitions.add(list.subList(i, Math.min(i + partitionSize, list.size())));
        }
        return partitions;
    }

    // ============ DIAGNOSTIC METHODS ============

    @Transactional(readOnly = true)
    public void debugCategoryPathMatching() {
        log.info("=== CATEGORY PATH MATCHING DIAGNOSTIC ===");

        List<Category> tekraCategories = categoryRepository.findAll().stream()
                .filter(cat -> cat.getTekraSlug() != null)
                .toList();

        log.info("Found {} Tekra categories", tekraCategories.size());

        tekraCategories.stream().limit(10).forEach(cat -> {
            log.info("Category: '{}' | tekraSlug='{}' | slug='{}' | path='{}'",
                    cat.getNameBg(),
                    cat.getTekraSlug(),
                    cat.getSlug(),
                    cat.getCategoryPath()
            );
        });

        log.info("\n=== TESTING PRODUCT MATCHING ===");

        for (Category cat : tekraCategories.stream().limit(3).toList()) {
            try {
                List<Map<String, Object>> products = tekraApiService.getProductsRaw(cat.getTekraSlug());

                if (products.isEmpty()) continue;

                Map<String, Object> sampleProduct = products.get(0);
                String sku = getStringValue(sampleProduct, "sku");
                String cat1 = getStringValue(sampleProduct, "category_1");
                String cat2 = getStringValue(sampleProduct, "category_2");
                String cat3 = getStringValue(sampleProduct, "category_3");

                String xmlPath = buildCategoryPath(cat1, cat2, cat3);

                log.info("\nProduct: {}", sku);
                log.info("  XML Categories: L1='{}' L2='{}' L3='{}'", cat1, cat2, cat3);
                log.info("  XML normalized: '{}'", xmlPath);
                log.info("  DB category: '{}'", cat.getNameBg());
                log.info("  DB path: '{}'", cat.getCategoryPath());
                log.info("  MATCH: {}", xmlPath != null && xmlPath.equalsIgnoreCase(cat.getCategoryPath()));

                if (cat3 != null) {
                    String normalized = normalizeCategoryForPath(cat3);
                    log.info("  cat3 '{}' normalizes to '{}' (tekraSlug='{}')",
                            cat3, normalized, cat.getTekraSlug());
                }

            } catch (Exception e) {
                log.error("Error testing category {}: {}", cat.getNameBg(), e.getMessage());
            }
        }

        log.info("===========================================");
    }

    // ============ INTERNAL RESULT CLASSES ============

    private static class CategorySyncResult {
        long processed;
        long created;
        long updated;
        long errors;

        CategorySyncResult(long processed, long created, long updated, long errors) {
            this.processed = processed;
            this.created = created;
            this.updated = updated;
            this.errors = errors;
        }
    }

    private static class ChunkResult {
        long processed;
        long created;
        long updated;
        long errors;

        ChunkResult(long processed, long created, long updated, long errors) {
            this.processed = processed;
            this.created = created;
            this.updated = updated;
            this.errors = errors;
        }
    }
}