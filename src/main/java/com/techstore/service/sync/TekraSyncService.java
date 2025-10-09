package com.techstore.service.sync;

import com.techstore.entity.Category;
import com.techstore.entity.Manufacturer;
import com.techstore.entity.Parameter;
import com.techstore.entity.ParameterOption;
import com.techstore.entity.Product;
import com.techstore.entity.ProductParameter;
import com.techstore.entity.SyncLog;
import com.techstore.enums.ProductStatus;
import com.techstore.repository.CategoryRepository;
import com.techstore.repository.ManufacturerRepository;
import com.techstore.repository.ParameterOptionRepository;
import com.techstore.repository.ParameterRepository;
import com.techstore.repository.ProductRepository;
import com.techstore.service.TekraApiService;
import com.techstore.util.LogHelper;
import com.techstore.util.SyncHelper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.techstore.util.LogHelper.LOG_STATUS_FAILED;
import static com.techstore.util.LogHelper.LOG_STATUS_SUCCESS;

@Slf4j
@Service
@RequiredArgsConstructor
public class TekraSyncService {


    private final CategoryRepository categoryRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final ProductRepository productRepository;
    private final ParameterRepository parameterRepository;
    private final ParameterOptionRepository parameterOptionRepository;
    private final EntityManager entityManager;
    private final TekraApiService tekraApiService;
    private final LogHelper logHelper;
    private final SyncHelper syncHelper;

    @Transactional
    public void syncTekraCategories() {
        SyncLog syncLog = logHelper.createSyncLogSimple("TEKRA_CATEGORIES");
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting Tekra categories synchronization with parent validation");

            List<Map<String, Object>> externalCategories = tekraApiService.getCategoriesRaw();

            if (externalCategories.isEmpty()) {
                log.warn("No categories returned from Tekra API");
                logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, 0, 0, 0, 0, "No categories found", startTime);
                return;
            }

            Map<String, Object> mainCategory = externalCategories.stream()
                    .filter(extCategory -> "videonablyudenie".equals(getString(extCategory, "slug")))
                    .findFirst()
                    .orElse(null);

            if (mainCategory == null) {
                log.warn("Category 'videonablyudenie' not found in Tekra API");
                logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, 0, 0, 0, 0, "Main category not found", startTime);
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
                logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, 1, created, updated, 0, "No subcategories", startTime);
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
            logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, totalCategories, created, updated, skipped,
                    skipped > 0 ? String.format("Skipped %d categories", skipped) : null, startTime);

            log.info("Tekra categories sync completed - Total: {}, Created: {}, Updated: {}, Skipped: {}",
                    totalCategories, created, updated, skipped);

        } catch (Exception e) {
            log.error("=== SYNC FAILED ===", e);
            logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, e.getMessage(), startTime);
            throw e;
        }
    }

    @Transactional
    public void syncTekraManufacturers() {
        SyncLog syncLog = logHelper.createSyncLogSimple("TEKRA_MANUFACTURERS");
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting Tekra manufacturers synchronization");

            List<Category> tekraCategories = categoryRepository.findAll().stream()
                    .filter(cat -> cat.getTekraSlug() != null)
                    .toList();

            if (tekraCategories.isEmpty()) {
                log.warn("No Tekra categories found");
                logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, 0, 0, 0, 0, "No Tekra categories found", startTime);
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
                logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, 0, 0, 0, 0, "No manufacturers found", startTime);
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

            logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, (long) allTekraManufacturers.size(),
                    created, updated, errors,
                    errors > 0 ? String.format("Completed with %d errors", errors) : null, startTime);

        } catch (Exception e) {
            logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, e.getMessage(), startTime);
            throw e;
        }
    }

    @Transactional
    public void syncTekraParameters() {
        SyncLog syncLog = logHelper.createSyncLogSimple("TEKRA_PARAMETERS");
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting Tekra parameters synchronization - FIXED VERSION");

            List<Category> tekraCategories = categoryRepository.findAll().stream()
                    .filter(cat -> cat.getTekraSlug() != null)
                    .toList();

            if (tekraCategories.isEmpty()) {
                log.error("No Tekra categories found");
                logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, "No Tekra categories found", startTime);
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
                        String sku = getString(product, "sku");
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
                logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0,
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

            logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, totalProcessed, totalCreated, totalUpdated,
                    totalErrors, message, startTime);

            log.info("=== Tekra parameters sync complete! ===");
            log.info("Total: {} params, {} options", totalCreated + totalUpdated,
                    totalParameterOptionsCreated + totalParameterOptionsUpdated);

        } catch (Exception e) {
            logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, e.getMessage(), startTime);
            log.error("Tekra parameters sync failed", e);
            throw e;
        }
    }

    @Transactional
    public void syncTekraProducts() {
        SyncLog syncLog = logHelper.createSyncLogSimple("TEKRA_PRODUCTS");
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
                        String sku = getString(product, "sku");
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
                            getString(p, "sku"),
                            getString(p, "name"),
                            getString(p, "category_1"),
                            getString(p, "category_2"),
                            getString(p, "category_3")
                    )
            );

            if (allProducts.isEmpty()) {
                log.warn("No products found in any category");
                logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, 0, 0, 0, 0, "No products found", startTime);
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
                    String sku = getString(rawProduct, "sku");
                    String name = getString(rawProduct, "name");

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
                            getString(rawProduct, "sku"), e.getMessage(), e);
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

            logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, totalProcessed, totalCreated,
                    totalUpdated, totalErrors, message, startTime);

            log.info("=== COMPLETE: Products sync finished in {}ms ===",
                    System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, e.getMessage(), startTime);
            log.error("=== FAILED: Products synchronization error ===", e);
            throw e;
        }
    }

    @Transactional
    private void fixDuplicateProducts() {
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

            product.setCategory(category);
            updateProductFieldsFromTekraXML(product, rawProduct, category.getTekraSlug());

            return product;

        } catch (Exception e) {
            log.error("Error in findOrCreateProduct for SKU {}: {}", sku, e.getMessage());
            throw e;
        }
    }

    private void updateProductFieldsFromTekraXML(Product product, Map<String, Object> rawData, String categorySlug) {
        try {
            product.setReferenceNumber(getString(rawData, "sku"));

            String name = getString(rawData, "name");
            product.setNameBg(name);
            product.setNameEn(name);

            product.setModel(getString(rawData, "model"));

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

            String description = getString(rawData, "description");
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
                log.warn("Product {} has no category set, trying fallback", product.getSku());
                String cat1 = getString(rawData, "category_1");
                String cat2 = getString(rawData, "category_2");
                String cat3 = getString(rawData, "category_3");

                if (cat3 != null) {
                    categoryRepository.findByNameBg(cat3).ifPresent(product::setCategory);
                } else if (cat2 != null) {
                    categoryRepository.findByNameBg(cat2).ifPresent(product::setCategory);
                } else if (cat1 != null) {
                    categoryRepository.findByNameBg(cat1).ifPresent(product::setCategory);
                }
            }

            String manufacturer = getString(rawData, "manufacturer");
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

        String primaryImage = getString(rawData, "image");
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
        if (manufacturerName == null || manufacturerName.trim().isEmpty()) {
            return;
        }

        String normalizedName = normalizeManufacturerName(manufacturerName);

        // Търсим по точно име
        Optional<Manufacturer> existingOpt = manufacturerRepository.findByName(manufacturerName);

        if (existingOpt.isEmpty()) {
            // Търсим по нормализирано име (за случаи с малки/големи букви или spacing)
            List<Manufacturer> allManufacturers = manufacturerRepository.findAll();

            for (Manufacturer m : allManufacturers) {
                if (normalizedName.equals(normalizeManufacturerName(m.getName()))) {
                    log.debug("Found existing manufacturer by normalized name: {} ≈ {}",
                            manufacturerName, m.getName());
                    product.setManufacturer(m);
                    return;
                }
            }

            // Създаваме нов
            Manufacturer manufacturer = new Manufacturer();
            manufacturer.setName(manufacturerName);
            manufacturer = manufacturerRepository.save(manufacturer);
            product.setManufacturer(manufacturer);
            log.info("Created new manufacturer: {}", manufacturerName);
        } else {
            product.setManufacturer(existingOpt.get());
        }
    }

    private String normalizeManufacturerName(String name) {
        if (name == null) return "";

        return name.toLowerCase()
                .trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[^a-zа-я0-9\\s]+", "");  // Премахва специални символи
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

    private ParameterOption findOrCreateParameterOption(Parameter parameter, String value) {
        try {
            // Нормализираме стойността за по-добро сравнение
            String normalizedValue = normalizeParameterValue(value);

            // СТЪПКА 1: Търсим по точно съвпадение
            Optional<ParameterOption> option = parameterOptionRepository
                    .findByParameterAndNameBg(parameter, value);

            if (option.isPresent()) {
                return option.get();
            }

            // СТЪПКА 2: Търсим по нормализирана стойност (case-insensitive)
            List<ParameterOption> allOptions = parameterOptionRepository
                    .findByParameterIdOrderByOrderAsc(parameter.getId());

            for (ParameterOption opt : allOptions) {
                String optNormalizedBg = normalizeParameterValue(opt.getNameBg());
                String optNormalizedEn = normalizeParameterValue(opt.getNameEn());

                if (normalizedValue.equals(optNormalizedBg) ||
                        normalizedValue.equals(optNormalizedEn)) {
                    log.debug("Found existing parameter option by normalized value: {} ≈ {}",
                            value, opt.getNameBg());
                    return opt;
                }
            }

            // СТЪПКА 3: Създаваме нова опция
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

    private String normalizeParameterValue(String value) {
        if (value == null) return "";

        return value.toLowerCase()
                .trim()
                .replaceAll("\\s+", " ");
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

    private Map<String, String> extractTekraParameters(Map<String, Object> rawProduct) {
        Map<String, String> parameters = new HashMap<>();

        for (Map.Entry<String, Object> entry : rawProduct.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (key != null && key.startsWith("prop_") && value != null) {
                String paramKey = key.substring(5);
                String paramValue = value.toString().trim();

                if (!paramValue.isEmpty()) {
                    parameters.put(paramKey, paramValue);
                    log.trace("Found parameter: {} = {}", paramKey, paramValue);
                }
            }
        }

        if (parameters.isEmpty()) {
            String[] possibleParameters = {
                    "cvjat", "merna", "model", "rezolyutsiya", "ir_podsvetka",
                    "razmer", "zvuk", "wdr", "obektiv", "korpus",
                    "stepen_na_zashtita", "kompresiya", "poe_portove",
                    "broy_izhodi", "raboten_tok", "moshtnost", "seriya_eco"
            };

            for (String paramKey : possibleParameters) {
                String value = getString(rawProduct, paramKey);
                if (value != null && !value.trim().isEmpty()) {
                    parameters.put(paramKey, value.trim());
                }
            }
        }

        return parameters;
    }

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

    private Category findMostSpecificCategory(Map<String, Object> product,
                                              Map<String, Category> categoriesByName,
                                              Map<String, Category> categoriesBySlug,
                                              Map<String, Category> categoriesByTekraSlug,
                                              Map<String, Integer> matchTypeStats) {

        final String category3Raw = getString(product, "category_3");
        final String category2Raw = getString(product, "category_2");
        final String category1Raw = getString(product, "category_1");
        final String sku = getString(product, "sku");

        final String category3 = "null".equalsIgnoreCase(category3Raw) ? null : category3Raw;
        final String category2 = "null".equalsIgnoreCase(category2Raw) ? null : category2Raw;
        final String category1 = "null".equalsIgnoreCase(category1Raw) ? null : category1Raw;

        String expectedPath = syncHelper.buildCategoryPath(category1, category2, category3);

        if (expectedPath == null) {
            log.warn("Product {}: Cannot build path (all categories null)", sku);
            matchTypeStats.put("no_match", matchTypeStats.get("no_match") + 1);
            return null;
        }

        log.debug("Product {}: Expected path '{}'", sku, expectedPath);

        // СТРАТЕГИЯ 1: Точно съвпадение на пълния път (работи за L1/L2/L3)
        Optional<Category> exactMatch = categoryRepository.findAll().stream()
                .filter(cat -> cat.getCategoryPath() != null)
                .filter(cat -> expectedPath.equalsIgnoreCase(cat.getCategoryPath()))
                .findFirst();

        if (exactMatch.isPresent() && isValidCategory(exactMatch.get())) {
            log.info("✓✓✓ EXACT PATH: {} -> '{}'", sku, exactMatch.get().getNameBg());
            matchTypeStats.put("perfect_path", matchTypeStats.get("perfect_path") + 1);
            return exactMatch.get();
        }

        // ✅ СТРАТЕГИЯ 2: Ако няма category_3, търси level-2 категория по ИМЕ на category_2
        if (category3 == null && category2 != null && category1 != null) {
            log.debug("Product {}: No category_3, searching for level-2 category by name", sku);

            // Първо търси по точно име
            Optional<Category> level2Match = categoryRepository.findAll().stream()
                    .filter(cat -> category2.equalsIgnoreCase(cat.getNameBg()))
                    .filter(cat -> cat.getParent() != null)
                    .filter(cat -> category1.equalsIgnoreCase(cat.getParent().getNameBg()))
                    .filter(this::isValidCategory)
                    .findFirst();

            if (level2Match.isPresent()) {
                log.info("✓✓ LEVEL-2 BY NAME: {} -> '{}' (parent: '{}')",
                        sku, level2Match.get().getNameBg(), level2Match.get().getParent().getNameBg());
                matchTypeStats.put("partial_path", matchTypeStats.get("partial_path") + 1);
                return level2Match.get();
            }

            // Ако не намери по име, търси по tekraSlug
            String normalizedCat2 = syncHelper.normalizeCategoryForPath(category2);
            Optional<Category> level2TekraMatch = categoryRepository.findAll().stream()
                    .filter(cat -> cat.getTekraSlug() != null)
                    .filter(cat -> normalizedCat2.equalsIgnoreCase(cat.getTekraSlug()))
                    .filter(cat -> cat.getParent() != null)
                    .filter(cat -> category1.equalsIgnoreCase(cat.getParent().getNameBg()))
                    .filter(this::isValidCategory)
                    .findFirst();

            if (level2TekraMatch.isPresent()) {
                log.info("✓✓ LEVEL-2 BY TEKRA SLUG: {} -> '{}' (tekraSlug: '{}')",
                        sku, level2TekraMatch.get().getNameBg(), level2TekraMatch.get().getTekraSlug());
                matchTypeStats.put("partial_path", matchTypeStats.get("partial_path") + 1);
                return level2TekraMatch.get();
            }
        }

        // СТРАТЕГИЯ 3: Частично съвпадение (L1+L2) чрез path
        if (category2 != null) {
            String partialPath = syncHelper.buildCategoryPath(category1, category2, null);

            Optional<Category> partialMatch = categoryRepository.findAll().stream()
                    .filter(cat -> cat.getCategoryPath() != null)
                    .filter(cat -> partialPath.equalsIgnoreCase(cat.getCategoryPath()))
                    .findFirst();

            if (partialMatch.isPresent() && isValidCategory(partialMatch.get())) {
                log.info("✓✓ PARTIAL PATH (L1+L2): {} -> '{}' | L3 '{}' not found",
                        sku, partialMatch.get().getNameBg(), category3);
                matchTypeStats.put("partial_path", matchTypeStats.get("partial_path") + 1);
                return partialMatch.get();
            }
        }

        // СТРАТЕГИЯ 4: Само L1
        if (category1 != null) {
            String l1Path = syncHelper.buildCategoryPath(category1, null, null);

            Optional<Category> l1Match = categoryRepository.findAll().stream()
                    .filter(cat -> cat.getCategoryPath() != null)
                    .filter(cat -> l1Path.equalsIgnoreCase(cat.getCategoryPath()))
                    .findFirst();

            if (l1Match.isPresent() && isValidCategory(l1Match.get())) {
                log.info("✓ L1 FALLBACK: {} -> '{}'", sku, l1Match.get().getNameBg());
                matchTypeStats.put("partial_path", matchTypeStats.get("partial_path") + 1);
                return l1Match.get();
            }
        }

        // СТРАТЕГИЯ 5: tekraSlug (за level-3 категории)
        if (category3 != null) {
            String normalizedCat3 = syncHelper.normalizeCategoryForPath(category3);
            Optional<Category> match = categoryRepository.findAll().stream()
                    .filter(cat -> normalizedCat3.equalsIgnoreCase(cat.getTekraSlug()))
                    .filter(this::isValidCategory)
                    .findFirst();

            if (match.isPresent()) {
                log.info("✓ TEKRA SLUG (L3): {} -> '{}'", sku, match.get().getNameBg());
                matchTypeStats.put("name_match", matchTypeStats.get("name_match") + 1);
                return match.get();
            }
        }

        if (category2 != null) {
            String normalizedCat2 = syncHelper.normalizeCategoryForPath(category2);
            Optional<Category> match = categoryRepository.findAll().stream()
                    .filter(cat -> normalizedCat2.equalsIgnoreCase(cat.getTekraSlug()))
                    .filter(this::isValidCategory)
                    .findFirst();

            if (match.isPresent()) {
                log.info("✓ TEKRA SLUG (L2): {} -> '{}'", sku, match.get().getNameBg());
                matchTypeStats.put("name_match", matchTypeStats.get("name_match") + 1);
                return match.get();
            }
        }

        // СТРАТЕГИЯ 6: име (последен fallback)
        if (category3 != null) {
            Optional<Category> match = categoryRepository.findAll().stream()
                    .filter(cat -> category3.equalsIgnoreCase(cat.getNameBg()))
                    .filter(this::isValidCategory)
                    .findFirst();

            if (match.isPresent()) {
                log.info("✓ NAME (L3): {} -> '{}'", sku, match.get().getNameBg());
                matchTypeStats.put("name_match", matchTypeStats.get("name_match") + 1);
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
                matchTypeStats.put("name_match", matchTypeStats.get("name_match") + 1);
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
                matchTypeStats.put("name_match", matchTypeStats.get("name_match") + 1);
                return match.get();
            }
        }

        log.warn("✗✗✗ NO MATCH: {} | Path: '{}'", sku, expectedPath);
        matchTypeStats.put("no_match", matchTypeStats.get("no_match") + 1);

        return null;
    }

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

            // ✅ ПОДОБРЕНА ПРОВЕРКА: Включва проверка по име за Vali категории
            Optional<Category> existingCategoryOpt = findExistingCategoryByTekraData(
                    tekraId, tekraSlug, name, parentCategory);

            Category category;
            boolean isNew = false;
            boolean isReused = false;

            if (existingCategoryOpt.isPresent()) {
                category = existingCategoryOpt.get();

                // Проверка дали това е Vali категория (няма tekraId)
                if (category.getTekraId() == null) {
                    isReused = true;
                    log.info("✓✓ REUSING existing Vali category: '{}' (ID: {}) for Tekra category '{}'",
                            category.getNameBg(), category.getId(), name);
                }

                // Проверка дали parent съвпада
                boolean parentMatches = parentMatches(category, parentCategory);

                if (!parentMatches) {
                    log.warn("Found category but WRONG parent! Creating NEW category.");
                    category = new Category();
                    isNew = true;
                }
            } else {
                category = new Category();
                isNew = true;
            }

            // ✅ Задаваме Tekra полета (дори ако преизползваме Vali категория)
            category.setTekraId(tekraId);
            category.setTekraSlug(tekraSlug);

            // Запазваме оригиналното име ако вече има (от Vali)
            if (category.getNameBg() == null || isNew) {
                category.setNameBg(name);
            }
            if (category.getNameEn() == null || isNew) {
                category.setNameEn(name);
            }

            category.setParent(parentCategory);

            String countStr = getString(rawData, "count");
            if (countStr != null && !countStr.isEmpty()) {
                try {
                    Integer count = Integer.parseInt(countStr);
                    category.setSortOrder(count);
                } catch (NumberFormatException e) {
                    category.setSortOrder(0);
                }
            } else {
                category.setSortOrder(0);
            }

            // Генериране на уникален slug
            String uniqueSlug;
            if (category.getSlug() != null && !isNew) {
                // Запазваме съществуващия slug ако категорията вече съществува
                uniqueSlug = category.getSlug();
            } else {
                uniqueSlug = generateUniqueSlug(tekraSlug, name, parentCategory, existingCategories);
            }
            category.setSlug(uniqueSlug);

            category.setCategoryPath(category.generateCategoryPath());

            category = categoryRepository.save(category);
            categoryRepository.flush();

            if (isReused) {
                log.info("✓✓✓ REUSED Vali category: '{}' | slug='{}' | path='{}' | tekraId={}",
                        name, uniqueSlug, category.getCategoryPath(), tekraId);
            } else if (isNew) {
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

    private Optional<Category> findExistingCategoryByTekraData(String tekraId,
                                                               String tekraSlug,
                                                               String categoryName,
                                                               Category parentCategory) {
        // СТЪПКА 1: Проверка по tekraId (ако вече е синхронизирана от Tekra)
        if (tekraId != null) {
            List<Category> byTekraId = categoryRepository.findAll().stream()
                    .filter(cat -> tekraId.equals(cat.getTekraId()))
                    .toList();

            if (!byTekraId.isEmpty()) {
                for (Category cat : byTekraId) {
                    if (parentMatches(cat, parentCategory)) {
                        log.debug("Found existing Tekra category by tekraId: {}", tekraId);
                        return Optional.of(cat);
                    }
                }
            }
        }

        // СТЪПКА 2: Проверка по tekraSlug
        if (tekraSlug != null) {
            List<Category> byTekraSlug = categoryRepository.findAll().stream()
                    .filter(cat -> tekraSlug.equals(cat.getTekraSlug()))
                    .toList();

            if (!byTekraSlug.isEmpty()) {
                for (Category cat : byTekraSlug) {
                    if (parentMatches(cat, parentCategory)) {
                        log.debug("Found existing Tekra category by tekraSlug: {}", tekraSlug);
                        return Optional.of(cat);
                    }
                }
            }
        }

        // ✅ СТЪПКА 3: Проверка по име (за категории от Vali, които имат същото име)
        if (categoryName != null && !categoryName.trim().isEmpty()) {
            String normalizedName = normalizeCategoryName(categoryName);

            List<Category> allCategories = categoryRepository.findAll();

            for (Category cat : allCategories) {
                // Проверяваме нормализирано име (без регистър, специални символи)
                String catNameBg = normalizeCategoryName(cat.getNameBg());
                String catNameEn = normalizeCategoryName(cat.getNameEn());

                if (normalizedName.equals(catNameBg) || normalizedName.equals(catNameEn)) {
                    // Проверка дали parent съвпада
                    if (parentMatches(cat, parentCategory)) {
                        log.info("✓ Found existing Vali category by name: '{}' (ID: {}, will REUSE instead of creating new)",
                                cat.getNameBg(), cat.getId());
                        return Optional.of(cat);
                    }
                }
            }
        }

        return Optional.empty();
    }

    private boolean parentMatches(Category category, Category expectedParent) {
        if (expectedParent == null && category.getParent() == null) {
            return true;
        }
        if (expectedParent != null && category.getParent() != null) {
            return expectedParent.getId().equals(category.getParent().getId());
        }
        return false;
    }

    private String generateUniqueSlug(String tekraSlug, String categoryName,
                                      Category parentCategory,
                                      Map<String, Category> existingCategories) {
        if (tekraSlug == null || tekraSlug.isEmpty()) {
            tekraSlug = syncHelper.createSlugFromName(categoryName);
        }

        String baseSlug = tekraSlug;

        if (parentCategory == null) {
            if (!syncHelper.slugExistsInMap(baseSlug, existingCategories) &&
                    !syncHelper.slugExistsInDatabase(baseSlug, null)) {
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

        if (!syncHelper.slugExistsInMap(hierarchicalSlug, existingCategories) &&
                !syncHelper.slugExistsInDatabase(hierarchicalSlug, parentCategory)) {
            return hierarchicalSlug;
        }

        Category existing = existingCategories.get(hierarchicalSlug);
        if (existing != null && existing.getParent() != null && parentCategory != null &&
                existing.getParent().getId().equals(parentCategory.getId())) {
            return hierarchicalSlug;
        }

        String discriminator = syncHelper.extractDiscriminator(categoryName);
        if (discriminator != null && !discriminator.isEmpty()) {
            String discriminatedSlug = hierarchicalSlug + "-" + discriminator;
            if (!syncHelper.slugExistsInMap(discriminatedSlug, existingCategories) &&
                    !syncHelper.slugExistsInDatabase(discriminatedSlug, parentCategory)) {
                return discriminatedSlug;
            }
        }

        int counter = 2;
        String numberedSlug;
        do {
            numberedSlug = hierarchicalSlug + "-" + counter;
            counter++;
        } while ((syncHelper.slugExistsInMap(numberedSlug, existingCategories) ||
                syncHelper.slugExistsInDatabase(numberedSlug, parentCategory)) && counter < 100);

        return numberedSlug;
    }

    private String normalizeCategoryName(String name) {
        if (name == null) return "";

        return name.toLowerCase()
                .trim()
                .replaceAll("[^a-zа-я0-9]+", "")
                .replaceAll("\\s+", "");
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
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
}
