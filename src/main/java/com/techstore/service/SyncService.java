package com.techstore.service;

import com.techstore.dto.external.ImageDto;
import com.techstore.dto.request.CategoryRequestFromExternalDto;
import com.techstore.dto.request.DocumentRequestDto;
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
import com.techstore.entity.ProductDocument;
import com.techstore.entity.ProductParameter;
import com.techstore.entity.SyncLog;
import com.techstore.enums.ProductStatus;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.repository.CategoryRepository;
import com.techstore.repository.ManufacturerRepository;
import com.techstore.repository.ParameterOptionRepository;
import com.techstore.repository.ParameterRepository;
import com.techstore.repository.ProductDocumentRepository;
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
    private final ProductDocumentRepository productDocumentRepository;
    private final CachedLookupService cachedLookupService;
    private final TekraApiService tekraApiService;
//    private final SyncService selfSyncService;

    @Value("#{'${excluded.categories.external-ids}'.split(',')}")
    private Set<Long> excludedCategories;

    @Value("${app.sync.enabled:true}")
    private boolean syncEnabled;

    @Value("${app.sync.batch-size:30}")
    private int batchSize;

    @Value("${app.sync.max-chunk-duration-minutes:5}")
    private int maxChunkDurationMinutes;

    @Value("${app.sync.tekra.enabled:false}")
    private boolean tekraSyncEnabled;

    @Value("${app.sync.tekra.auto-sync:false}")
    private boolean tekraAutoSync;

    // ============ SCHEDULED SYNC ============
//    @Scheduled(cron = "${app.sync.cron}")
//    public void scheduledSync() {
//        if (!syncEnabled) {
//            log.info("Synchronization is disabled");
//            return;
//        }
//
//        log.info("Starting scheduled synchronization at {}", LocalDateTime.now());
//        try {
//            selfSyncService.syncCategories();
//            log.info("Scheduled category synchronization completed at {}", LocalDateTime.now());
//
//            selfSyncService.syncManufacturers();
//            log.info("Scheduled manufacturers synchronization completed at {}", LocalDateTime.now());
//
//            selfSyncService.syncParameters();
//            log.info("Scheduled parameters synchronization completed at {}", LocalDateTime.now());
//
//            selfSyncService.syncProducts();
//            log.info("Scheduled products synchronization completed at {}", LocalDateTime.now());
//
//            selfSyncService.syncDocuments();
//            log.info("Scheduled documents synchronization completed at {}", LocalDateTime.now());
//
//            if (tekraSyncEnabled && tekraAutoSync) {
//                log.info("Starting scheduled Tekra Wildlife Surveillance sync");
//                selfSyncService.syncTekraWildlifeSurveillance();
//                log.info("Scheduled Tekra sync completed at {}", LocalDateTime.now());
//            }
//
//        } catch (Exception e) {
//            log.error("CRITICAL: Scheduled synchronization failed", e);
//        }
//    }

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

    private ParameterOption createParameterOptionFromExternal(ParameterOptionRequestDto extOption, Parameter parameter) {
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
            log.error("Error creating parameter option from external data: {}", e.getMessage());
            return null;
        }
    }

    private void updateParameterOptionFromExternal(ParameterOption option, ParameterOptionRequestDto extOption) {
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
            log.error("Error updating parameter option: {}", e.getMessage());
        }
    }

    private void syncValiParameterOptions(Parameter parameter, List<ParameterOptionRequestDto> externalOptions) {
        if (externalOptions == null || externalOptions.isEmpty()) {
            log.debug("No options provided for parameter: {}", parameter.getNameBg());
            return;
        }

        // Get existing options for this parameter
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
                // Create new option
                option = createValiParameterOptionFromExternal(extOption, parameter);
                if (option != null) {
                    created++;
                }
            } else {
                // Update existing option
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

    @Transactional
    public void syncParameters() {
        SyncLog syncLog = createSyncLogSimple("PARAMETERS");
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting Vali parameters synchronization with options");

            List<Category> categories = categoryRepository.findAll();
            long totalProcessed = 0, created = 0, updated = 0, errors = 0;
            long optionsCreated = 0, optionsUpdated = 0;

            for (Category category : categories) {
                try {
                    Map<String, Parameter> existingParameters = cachedLookupService.getParametersByCategory(category);
                    List<ParameterRequestDto> externalParameters = valiApiService.getParametersByCategory(category.getExternalId());

                    if (externalParameters.isEmpty()) {
                        log.debug("No parameters found for category: {}", category.getNameBg());
                        continue;
                    }

                    List<Parameter> parametersToSave = new ArrayList<>();

                    // First pass: Create/update parameters
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

                    // Save parameters first
                    if (!parametersToSave.isEmpty()) {
                        parameterRepository.saveAll(parametersToSave);

                        // Second pass: Create/update parameter options
                        for (int i = 0; i < parametersToSave.size(); i++) {
                            Parameter parameter = parametersToSave.get(i);
                            ParameterRequestDto extParam = externalParameters.get(i);

                            try {
                                // Only sync options for Vali API parameters (have external_id)
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
                // Find parameter - try cache first, then repository
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

                // Find parameter option - try cache first, then repository
                Optional<ParameterOption> optionOpt = cachedLookupService
                        .getParameterOption(paramValue.getOptionId(), parameter.getId());

                if (optionOpt.isEmpty()) {
                    // Try to find by external ID
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

                // Create ProductParameter
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

    @Transactional
    public void syncDocuments() {
        SyncLog syncLog = createSyncLogSimple("DOCUMENTS");
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting documents synchronization");

            List<DocumentRequestDto> externalDocuments = valiApiService.getAllDocuments();

            if (externalDocuments.isEmpty()) {
                log.info("No documents found in external API");
                updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, 0, 0, 0, 0, null, startTime);
                return;
            }

            List<List<DocumentRequestDto>> chunks = partitionList(externalDocuments, batchSize);
            log.info("Processing {} documents in {} chunks", externalDocuments.size(), chunks.size());

            long totalProcessed = 0, created = 0, updated = 0, errors = 0;

            for (int i = 0; i < chunks.size(); i++) {
                List<DocumentRequestDto> chunk = chunks.get(i);

                try {
                    DocumentChunkResult result = processDocumentsChunk(chunk);
                    totalProcessed += result.processed;
                    created += result.created;
                    updated += result.updated;
                    errors += result.errors;

                    log.debug("Processed document chunk {}/{} - Created: {}, Updated: {}, Errors: {}",
                            i + 1, chunks.size(), result.created, result.updated, result.errors);

                    if (i < chunks.size() - 1) {
                        Thread.sleep(150);
                    }

                } catch (Exception e) {
                    log.error("Error processing document chunk {}/{}: {}", i + 1, chunks.size(), e.getMessage());
                    errors += chunk.size();
                }
            }

            updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, totalProcessed, created, updated, errors,
                    errors > 0 ? String.format("Completed with %d errors", errors) : null, startTime);
            log.info("Documents synchronization completed - Created: {}, Updated: {}, Errors: {}", created, updated, errors);

        } catch (Exception e) {
            updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, e.getMessage(), startTime);
            log.error("Error during documents synchronization", e);
            throw e;
        }
    }

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

            // Find the main surveillance category
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
                    .filter(cat -> cat.getTekraSlug() != null || cat.getTekraId() != null)
                    .collect(Collectors.toMap(
                            cat -> cat.getTekraSlug() != null ? cat.getTekraSlug() : cat.getTekraId(),
                            cat -> cat
                    ));

            long created = 0, updated = 0, skipped = 0;
            List<CategoryRelationship> relationships = new ArrayList<>();

            // Step 1: Create/update main category
            Category mainCat = createOrUpdateCategory(mainCategory, existingCategories, null);
            if (mainCat != null) {
                if (existingCategories.containsKey(mainCat.getTekraSlug())) {
                    updated++;
                } else {
                    created++;
                    existingCategories.put(mainCat.getTekraSlug(), mainCat);
                }
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

                        if (subCatId == null || subCatName == null) {
                            log.warn("Skipping subcategory with missing fields: id={}, name={}", subCatId, subCatName);
                            skipped++;
                            continue;
                        }

                        // Create/update level 2 category
                        Category level2Cat = createOrUpdateCategory(subCat, existingCategories, null);
                        if (level2Cat != null) {
                            if (existingCategories.containsKey(level2Cat.getTekraSlug())) {
                                updated++;
                            } else {
                                created++;
                                existingCategories.put(level2Cat.getTekraSlug(), level2Cat);
                            }

                            // Store relationship for later
                            relationships.add(new CategoryRelationship(
                                    level2Cat.getTekraSlug(),
                                    mainCat.getTekraSlug()
                            ));

                            // Step 3: Process subsubcat (level 3)
                            Object subSubCatObj = subCat.get("subsubcat");
                            if (subSubCatObj instanceof List) {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> subSubCategories = (List<Map<String, Object>>) subSubCatObj;

                                if (!subSubCategories.isEmpty()) {
                                    log.debug("Found {} level-3 categories under '{}'",
                                            subSubCategories.size(), subCatName);
                                }

                                for (Map<String, Object> subSubCat : subSubCategories) {
                                    try {
                                        String subSubCatId = getString(subSubCat, "id");
                                        String subSubCatName = getString(subSubCat, "name");

                                        if (subSubCatId == null || subSubCatName == null) {
                                            log.warn("Skipping level-3 category with missing fields: id={}, name={}",
                                                    subSubCatId, subSubCatName);
                                            skipped++;
                                            continue;
                                        }

                                        // Create/update level 3 category
                                        Category level3Cat = createOrUpdateCategory(subSubCat, existingCategories, null);
                                        if (level3Cat != null) {
                                            if (existingCategories.containsKey(level3Cat.getTekraSlug())) {
                                                updated++;
                                            } else {
                                                created++;
                                                existingCategories.put(level3Cat.getTekraSlug(), level3Cat);
                                            }

                                            // Store relationship: level3 -> level2
                                            relationships.add(new CategoryRelationship(
                                                    level3Cat.getTekraSlug(),
                                                    level2Cat.getTekraSlug()
                                            ));
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

            // Step 4: Set up all parent-child relationships
            for (CategoryRelationship rel : relationships) {
                try {
                    Category child = existingCategories.get(rel.childSlug);
                    Category parent = existingCategories.get(rel.parentSlug);

                    if (child != null && parent != null && !child.equals(parent)) {
                        child.setParent(parent);
                        categoryRepository.save(child);
                        log.debug("Set parent for '{}' to '{}'", child.getNameBg(), parent.getNameBg());
                    }
                } catch (Exception e) {
                    log.error("Error setting parent relationship: {}", e.getMessage());
                }
            }

            long totalCategories = created + updated;
            updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, totalCategories, created, updated, skipped,
                    skipped > 0 ? String.format("Skipped %d categories with errors", skipped) : null, startTime);

            log.info("Tekra categories synchronization completed - Total: {}, Created: {}, Updated: {}, Skipped: {}",
                    totalCategories, created, updated, skipped);

            // Print hierarchy
            printCategoryHierarchy();

        } catch (Exception e) {
            updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, e.getMessage(), startTime);
            log.error("Error during Tekra categories synchronization", e);
            throw e;
        }
    }

    /**
     * Create or update a category from Tekra data
     */
    private Category createOrUpdateCategory(Map<String, Object> rawData,
                                            Map<String, Category> existingCategories,
                                            String parentSlug) {
        try {
            String tekraId = getString(rawData, "id");
            String slug = getString(rawData, "slug");
            String name = getString(rawData, "name");

            if (tekraId == null || slug == null || name == null) {
                log.warn("Cannot create category with missing required fields: id={}, slug={}, name={}",
                        tekraId, slug, name);
                return null;
            }

            Category category = existingCategories.get(slug);
            boolean isNew = (category == null);

            if (isNew) {
                category = new Category();
                category.setTekraId(tekraId);
                category.setTekraSlug(slug);
                category.setSlug(slug);
            }

            // Update fields
            category.setNameBg(name);
            category.setNameEn(name); // Can translate later if needed

            // Set show based on product count
            String countStr = getString(rawData, "count");
            if (countStr != null) {
                try {
                    Integer count = Integer.parseInt(countStr);
                    category.setSortOrder(count);
                    category.setShow(count > 0); // Only show if has products
                } catch (NumberFormatException e) {
                    category.setSortOrder(0);
                    category.setShow(true);
                }
            } else {
                category.setShow(true);
                category.setSortOrder(0);
            }

            category = categoryRepository.save(category);

            log.info("{} Tekra category: {} (slug: {}, id: {})",
                    isNew ? "Created" : "Updated", name, slug, tekraId);

            return category;

        } catch (Exception e) {
            log.error("Error creating/updating category from Tekra data", e);
            return null;
        }
    }

    /**
     * Helper class to store parent-child relationships for later processing
     */
    private static class CategoryRelationship {
        final String childSlug;
        final String parentSlug;

        CategoryRelationship(String childSlug, String parentSlug) {
            this.childSlug = childSlug;
            this.parentSlug = parentSlug;
        }
    }

    /**
     * Print the category hierarchy for verification
     */
    private void printCategoryHierarchy() {
        try {
            log.info("\n=== TEKRA CATEGORY HIERARCHY ===");

            List<Category> allCategories = categoryRepository.findAll().stream()
                    .filter(cat -> cat.getTekraSlug() != null)
                    .sorted((a, b) -> {
                        // Sort by hierarchy level and name
                        if (a.getParent() == null && b.getParent() != null) return -1;
                        if (a.getParent() != null && b.getParent() == null) return 1;
                        if (a.getParent() != null && b.getParent() != null) {
                            int parentCompare = a.getParent().getId().compareTo(b.getParent().getId());
                            if (parentCompare != 0) return parentCompare;
                        }
                        return a.getNameBg().compareTo(b.getNameBg());
                    })
                    .toList();

            for (Category category : allCategories) {
                long productCount = productRepository.countByCategoryId(category.getId());
                long parameterCount = parameterRepository.countByCategoryId(category.getId());

                String indent = "";
                Category temp = category;
                int level = 0;
                while (temp.getParent() != null) {
                    level++;
                    temp = temp.getParent();
                }

                if (level == 1) indent = "  ├─ ";
                else if (level == 2) indent = "    └─ ";
                else indent = "• ";

                log.info("{}{} (slug: {}) - {} products, {} parameters",
                        indent, category.getNameBg(), category.getTekraSlug(),
                        productCount, parameterCount);
            }

            log.info("================================\n");

        } catch (Exception e) {
            log.error("Error printing category hierarchy", e);
        }
    }

    private void updateProductFieldsFromTekraXML(Product product, Map<String, Object> rawData, String categorySlug) {
        // Basic identification
        product.setReferenceNumber(getStringValue(rawData, "sku")); // Use SKU as reference

        // Names
        String name = getStringValue(rawData, "name");
        product.setNameBg(name);
        product.setNameEn(name); // Both set to Bulgarian for now

        // Model and manufacturer
        product.setModel(getStringValue(rawData, "model"));

        // Prices from Tekra XML
        Double price = getDoubleValue(rawData, "price");
        if (price != null) {
            product.setPriceClient(BigDecimal.valueOf(price));
        }

        Double partnerPrice = getDoubleValue(rawData, "partner_price");
        if (partnerPrice != null) {
            product.setPricePartner(BigDecimal.valueOf(partnerPrice));
        }

        // Status and availability
        Integer quantity = getIntegerValue(rawData, "quantity");
        boolean inStock = (quantity != null && quantity > 0);
        product.setShow(inStock);
        product.setStatus(inStock ? ProductStatus.AVAILABLE : ProductStatus.NOT_AVAILABLE);

        // Description
        String description = getStringValue(rawData, "description");
        if (description != null) {
            product.setDescriptionBg(description);
            product.setDescriptionEn(description);
        }

        // Weight
        Double weight = getDoubleValue(rawData, "weight");
        if (weight == null) {
            weight = getDoubleValue(rawData, "net_weight");
        }
        if (weight != null && weight > 0) {
            product.setWeight(BigDecimal.valueOf(weight));
        }

        // Images from XML
        setImagesFromTekraXML(product, rawData);

        // Category mapping
        setCategoryFromTekraXML(product, rawData, categorySlug);

        // Manufacturer
        String manufacturer = getStringValue(rawData, "manufacturer");
        if (manufacturer != null) {
            setManufacturerFromName(product, manufacturer);
        }

        // Calculate final price
        product.calculateFinalPrice();
    }

    @SuppressWarnings("unchecked")
    private void setImagesFromTekraXML(Product product, Map<String, Object> rawData) {
        List<String> allImages = new ArrayList<>();

        // Primary image
        String primaryImage = getStringValue(rawData, "image");
        if (primaryImage != null && !primaryImage.isEmpty()) {
            allImages.add(primaryImage);
        }

        // Gallery images
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

        // Set primary and additional images
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
        // Try to map using category hierarchy from XML
        String category1 = getStringValue(rawData, "category_1"); // Main category
        String category2 = getStringValue(rawData, "category_2"); // Subcategory
        String category3 = getStringValue(rawData, "category_3"); // Sub-subcategory

        // Use the provided categorySlug first, then try to find by name
        if (categorySlug != null) {
            categoryRepository.findByTekraSlug(categorySlug)
                    .ifPresent(product::setCategory);
        } else if (category1 != null) {
            // Try to find category by Bulgarian name
            categoryRepository.findByNameBg(category1)
                    .or(() -> categoryRepository.findByNameEn(category1))
                    .ifPresent(product::setCategory);
        }
    }

    private void setManufacturerFromName(Product product, String manufacturerName) {
        manufacturerRepository.findByName(manufacturerName)
                .or(() -> {
                    // Create manufacturer if it doesn't exist
                    Manufacturer manufacturer = new Manufacturer();
                    manufacturer.setName(manufacturerName);
                    return Optional.of(manufacturerRepository.save(manufacturer));
                })
                .ifPresent(product::setManufacturer);
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

    @Transactional
    public void syncTekraManufacturers() {
        SyncLog syncLog = createSyncLogSimple("TEKRA_MANUFACTURERS");
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting Tekra manufacturers synchronization");

            // Get all Tekra categories
            List<Category> tekraCategories = categoryRepository.findAll().stream()
                    .filter(cat -> cat.getTekraSlug() != null)
                    .toList();

            if (tekraCategories.isEmpty()) {
                log.warn("No Tekra categories found");
                updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, 0, 0, 0, 0, "No Tekra categories found", startTime);
                return;
            }

            log.info("Extracting manufacturers from {} Tekra categories", tekraCategories.size());

            // Collect all unique manufacturers from all categories
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

    private Manufacturer createTekraManufacturer(String manufacturerName) {
        try {
            Manufacturer manufacturer = new Manufacturer();
            manufacturer.setName(manufacturerName);

            // Set some default information (you can enhance this later)
            manufacturer.setInformationName(manufacturerName);

            return manufacturer;
        } catch (Exception e) {
            log.error("Error creating Tekra manufacturer: {}", manufacturerName, e);
            return null;
        }
    }

    @Transactional
    public void syncTekraParameters() {
        SyncLog syncLog = createSyncLogSimple("TEKRA_PARAMETERS");
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting Tekra parameters synchronization for all categories");

            // Get all Tekra categories (not just videonablyudenie)
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

            // Process each category
            for (Category category : tekraCategories) {
                try {
                    log.info("Processing parameters for category: {} (slug: {})",
                            category.getNameBg(), category.getTekraSlug());

                    // Extract parameters from products in this category
                    Map<String, Set<String>> tekraParameters = tekraApiService
                            .extractTekraParametersFromProducts(category.getTekraSlug());

                    if (tekraParameters.isEmpty()) {
                        log.info("No parameters found for category: {}", category.getNameBg());
                        continue;
                    }

                    log.info("Extracted {} parameter types for category '{}'",
                            tekraParameters.size(), category.getNameBg());

                    // Get existing parameters for this category
                    Map<String, Parameter> existingParameters = parameterRepository.findByCategoryId(category.getId())
                            .stream()
                            .collect(Collectors.toMap(
                                    p -> p.getTekraKey() != null ? p.getTekraKey() : p.getNameBg(),
                                    p -> p,
                                    (existing, duplicate) -> existing
                            ));

                    long categoryParamsCreated = 0, categoryParamsUpdated = 0, categoryParamsErrors = 0;
                    long categoryOptionsCreated = 0, categoryOptionsUpdated = 0;

                    // Process each parameter type
                    for (Map.Entry<String, Set<String>> paramEntry : tekraParameters.entrySet()) {
                        try {
                            String parameterKey = paramEntry.getKey();
                            Set<String> parameterValues = paramEntry.getValue();

                            log.debug("Processing parameter: {} with {} values for category {}",
                                    parameterKey, parameterValues.size(), category.getNameBg());

                            // Convert parameter key to human-readable name
                            String parameterName = convertTekraParameterKeyToName(parameterKey);

                            // Find or create parameter
                            Parameter parameter = existingParameters.get(parameterKey);
                            boolean isNewParameter = false;

                            if (parameter == null) {
                                // Check by name as fallback
                                parameter = parameterRepository.findByCategoryAndNameBg(category, parameterName)
                                        .orElse(null);
                            }

                            if (parameter == null) {
                                // Create new parameter
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

                            // Sync parameter options for this parameter
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

                    // Update totals
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
                // Fallback: capitalize and replace underscores
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
                Map.entry("merna", 99) // Unit should be last
        );

        return orderMap.getOrDefault(parameterKey, 50); // Default middle position
    }

    @Transactional
    public void syncTekraProducts() {
        SyncLog syncLog = createSyncLogSimple("TEKRA_PRODUCTS");
        long startTime = System.currentTimeMillis();

        try {
            log.info("=== STARTING Tekra products synchronization ===");

            // STEP 1: Fetch products (this might be slow)
            log.info("STEP 1: Fetching products from Tekra API...");
            String mainCategorySlug = "videonablyudenie";

            // Use simple fetch instead of pagination to avoid hanging
            List<Map<String, Object>> rawProducts = tekraApiService.getProductsRaw(mainCategorySlug);

            log.info("STEP 1 COMPLETE: Fetched {} products", rawProducts.size());

            if (rawProducts.isEmpty()) {
                log.warn("No products returned from Tekra API");
                updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, 0, 0, 0, 0, "No products found", startTime);
                return;
            }

            // STEP 2: Load all categories
            log.info("STEP 2: Loading Tekra categories...");
            Map<String, Category> categoriesByName = new HashMap<>();
            Map<String, Category> categoriesBySlug = new HashMap<>();

            List<Category> allTekraCategories = categoryRepository.findAll().stream()
                    .filter(cat -> cat.getTekraSlug() != null)
                    .toList();

            for (Category cat : allTekraCategories) {
                if (cat.getTekraSlug() != null) {
                    categoriesBySlug.put(cat.getTekraSlug().toLowerCase(), cat);
                }
                if (cat.getNameBg() != null) {
                    categoriesByName.put(cat.getNameBg().toLowerCase(), cat);
                }
            }
            log.info("STEP 2 COMPLETE: Loaded {} categories", allTekraCategories.size());

            // STEP 3: Pre-load ALL parameters for ALL categories (avoid N+1)
            log.info("STEP 3: Pre-loading parameters for all categories...");
            Map<Long, Map<String, Parameter>> allCategoryParameters = new HashMap<>();

            for (Category cat : allTekraCategories) {
                List<Parameter> params = parameterRepository.findByCategoryId(cat.getId());
                if (!params.isEmpty()) {
                    Map<String, Parameter> paramMap = params.stream()
                            .collect(Collectors.toMap(
                                    p -> p.getTekraKey() != null ? p.getTekraKey() : p.getNameBg(),
                                    p -> p
                            ));
                    allCategoryParameters.put(cat.getId(), paramMap);
                }
            }
            log.info("STEP 3 COMPLETE: Loaded parameters for {} categories", allCategoryParameters.size());

            // STEP 4: Process products
            log.info("STEP 4: Processing {} products...", rawProducts.size());

            long totalProcessed = 0, totalCreated = 0, totalUpdated = 0, totalErrors = 0;
            long totalParametersLinked = 0;

            for (int i = 0; i < rawProducts.size(); i++) {
                Map<String, Object> rawProduct = rawProducts.get(i);

                try {
                    String sku = getStringValue(rawProduct, "sku");
                    String name = getStringValue(rawProduct, "name");

                    if (sku == null || name == null) {
                        totalErrors++;
                        continue;
                    }

                    // Find category
                    Category productCategory = findMostSpecificCategory(rawProduct, categoriesByName, categoriesBySlug);
                    if (productCategory == null) {
                        log.warn("No category for product: {} ({})", name, sku);
                        totalErrors++;
                        continue;
                    }

                    // Create or update product
                    Optional<Product> existingProduct = productRepository.findBySku(sku);
                    Product product;

                    if (existingProduct.isPresent()) {
                        product = existingProduct.get();
                        updateProductFieldsFromTekraXML(product, rawProduct, productCategory.getTekraSlug());
                        totalUpdated++;
                    } else {
                        product = new Product();
                        updateProductFieldsFromTekraXML(product, rawProduct, productCategory.getTekraSlug());
                        product.setSku(sku);
                        totalCreated++;
                    }

                    product.setCategory(productCategory);
                    product = productRepository.save(product);

                    // Link parameters (using pre-loaded data)
                    Map<String, Parameter> parametersLookup = allCategoryParameters.get(productCategory.getId());
                    if (parametersLookup != null && !parametersLookup.isEmpty()) {
                        ParameterMappingResult paramResult = setTekraParametersToProductEnhanced(
                                product, rawProduct, parametersLookup);
                        totalParametersLinked += paramResult.linked;

                        if (paramResult.linked > 0) {
                            productRepository.save(product);
                        }
                    }

                    totalProcessed++;

                    // Log progress every 10 products
                    if (totalProcessed % 10 == 0) {
                        log.info("Progress: {}/{} products processed ({} created, {} updated, {} errors)",
                                totalProcessed, rawProducts.size(), totalCreated, totalUpdated, totalErrors);
                    }

                    // Flush every 20 products to avoid memory issues
                    if (totalProcessed % 20 == 0) {
                        entityManager.flush();
                        entityManager.clear();
                    }

                } catch (Exception e) {
                    totalErrors++;
                    log.error("Error processing product #{}: {}", i, e.getMessage());
                }
            }

            log.info("STEP 4 COMPLETE: Processed all {} products", totalProcessed);

            String message = String.format("Products: %d created, %d updated, %d parameters linked",
                    totalCreated, totalUpdated, totalParametersLinked);
            if (totalErrors > 0) {
                message += String.format(", %d errors", totalErrors);
            }

            updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, totalProcessed, totalCreated, totalUpdated,
                    totalErrors, message, startTime);

            log.info("=== COMPLETE: Tekra products sync finished in {}ms ===",
                    System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, e.getMessage(), startTime);
            log.error("=== FAILED: Tekra products synchronization error ===", e);
            throw e;
        }
    }

    /**
     * Find the most specific category for a product
     */
    private Category findMostSpecificCategory(Map<String, Object> product,
                                              Map<String, Category> categoriesByName,
                                              Map<String, Category> categoriesBySlug) {
        String category3 = getStringValue(product, "category_3");
        String category2 = getStringValue(product, "category_2");
        String category1 = getStringValue(product, "category_1");

        // Try most specific first
        if (category3 != null && !category3.isEmpty()) {
            Category cat = findCategoryByName(category3, categoriesByName, categoriesBySlug);
            if (cat != null) return cat;
        }

        if (category2 != null && !category2.isEmpty()) {
            Category cat = findCategoryByName(category2, categoriesByName, categoriesBySlug);
            if (cat != null) return cat;
        }

        if (category1 != null && !category1.isEmpty()) {
            Category cat = findCategoryByName(category1, categoriesByName, categoriesBySlug);
            if (cat != null) return cat;
        }

        // Fallback
        return categoriesBySlug.get("videonablyudenie");
    }

    /**
     * Find category by name
     */
    private Category findCategoryByName(String categoryName,
                                        Map<String, Category> categoriesByName,
                                        Map<String, Category> categoriesBySlug) {
        if (categoryName == null || categoryName.isEmpty()) {
            return null;
        }

        String lowerName = categoryName.toLowerCase().trim();

        // Try exact match by name
        Category cat = categoriesByName.get(lowerName);
        if (cat != null) return cat;

        // Try by slug
        String slugified = createSlugFromName(categoryName);
        return categoriesBySlug.get(slugified);
    }

    @Transactional
    public void syncTekraComplete() {
        log.info("=== Starting complete Tekra synchronization with subcategories ===");
        long overallStart = System.currentTimeMillis();

        try {
            // Step 1: Sync categories (including subcategories)
            log.info("Step 1: Syncing Tekra categories and subcategories...");
            syncTekraCategories();

            long categoriesCount = categoryRepository.findAll().stream()
                    .filter(cat -> cat.getTekraSlug() != null)
                    .count();
            log.info("✓ Synced {} Tekra categories (including subcategories)", categoriesCount);

            // Step 2: Sync manufacturers from all categories
            log.info("Step 2: Syncing Tekra manufacturers from all categories...");
            syncTekraManufacturers();

            // Step 3: Sync parameters for each category (CRITICAL - must be before products)
            log.info("Step 3: Syncing Tekra parameters for all categories...");
            syncTekraParameters();

            // Optional: Clear cache before syncing products to ensure fresh data
            if (tekraApiService != null) {
                tekraApiService.clearCache();
            }

            // Step 4: Sync products for each category with parameters
            log.info("Step 4: Syncing Tekra products for all categories with parameters...");
            syncTekraProducts();

            long totalDuration = System.currentTimeMillis() - overallStart;
            log.info("=== Complete Tekra synchronization finished in {}ms ({} minutes) ===",
                    totalDuration, totalDuration / 60000);

            // Print summary
            printTekraSyncSummary();

        } catch (Exception e) {
            long totalDuration = System.currentTimeMillis() - overallStart;
            log.error("=== Complete Tekra synchronization failed after {}ms ===", totalDuration, e);
            throw e;
        }
    }

    /**
     * Print a summary of the Tekra sync results
     */
    private void printTekraSyncSummary() {
        try {
            List<Category> tekraCategories = categoryRepository.findAll().stream()
                    .filter(cat -> cat.getTekraSlug() != null)
                    .toList();

            log.info("=== TEKRA SYNC SUMMARY ===");
            log.info("Total Tekra categories: {}", tekraCategories.size());

            for (Category category : tekraCategories) {
                long productCount = productRepository.countByCategoryId(category.getId());
                long parameterCount = parameterRepository.countByCategoryId(category.getId());

                String indent = category.getParent() != null ? "  └─ " : "• ";
                log.info("{}{} (slug: {}) - {} products, {} parameters",
                        indent, category.getNameBg(), category.getTekraSlug(),
                        productCount, parameterCount);
            }

            log.info("==========================");

        } catch (Exception e) {
            log.error("Error printing sync summary", e);
        }
    }

    private ParameterMappingResult setTekraParametersToProductEnhanced(
            Product product, Map<String, Object> rawData, Map<String, Parameter> parametersLookup) {

        int linked = 0, created = 0;

        if (product.getCategory() == null) {
            log.warn("Product {} has no category, cannot set parameters", product.getSku());
            return new ParameterMappingResult(0, 0);
        }

        try {
            Set<ProductParameter> productParameters = new HashSet<>();

            log.debug("Processing parameters for product: {} (Category: {})",
                    product.getSku(), product.getCategory().getNameBg());

            // Process all prop_* fields as parameters
            for (Map.Entry<String, Object> entry : rawData.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (key.startsWith("prop_") && value != null) {
                    String parameterKey = key.substring(5); // Remove "prop_" prefix
                    String parameterValue = value.toString().trim();

                    if (!parameterValue.isEmpty()) {
                        Parameter parameter = parametersLookup.get(parameterKey);

                        if (parameter != null) {
                            ParameterOption option = findOrCreateParameterOption(parameter, parameterValue);

                            if (option != null) {
                                ProductParameter pp = new ProductParameter();
                                pp.setProduct(product);
                                pp.setParameter(parameter);
                                pp.setParameterOption(option);
                                productParameters.add(pp);
                                linked++;

                                log.debug("Linked parameter: {} = {} for product {}",
                                        parameter.getNameBg(), option.getNameBg(), product.getSku());

                                if (option.getId() == null) {
                                    created++; // This was a newly created option
                                }
                            } else {
                                log.warn("Could not create parameter option: {} = {} for parameter {}",
                                        parameterKey, parameterValue, parameter.getNameBg());
                            }
                        } else {
                            log.debug("Parameter not found for key: {} (available keys: {})",
                                    parameterKey, parametersLookup.keySet().stream()
                                            .limit(5).collect(Collectors.toList()));
                        }
                    }
                }
            }

            // Clear existing parameters and set new ones
            if (product.getProductParameters() != null) {
                product.getProductParameters().clear();
            } else {
                product.setProductParameters(new HashSet<>());
            }

            product.getProductParameters().addAll(productParameters);

            log.debug("Set {} parameters for product {}", productParameters.size(), product.getSku());

        } catch (Exception e) {
            log.error("Error setting Tekra parameters to product {}: {}", product.getSku(), e.getMessage(), e);
        }

        return new ParameterMappingResult(linked, created);
    }

    private ParameterOption findOrCreateParameterOption(Parameter parameter, String value) {
        try {
            // Try exact match first
            Optional<ParameterOption> option = parameterOptionRepository.findByParameterAndNameBg(parameter, value);

            if (option.isPresent()) {
                return option.get();
            }

            // Try case-insensitive match
            List<ParameterOption> allOptions = parameterOptionRepository.findByParameterIdOrderByOrderAsc(parameter.getId());

            for (ParameterOption opt : allOptions) {
                if (value.equalsIgnoreCase(opt.getNameBg()) ||
                        (opt.getNameEn() != null && value.equalsIgnoreCase(opt.getNameEn()))) {
                    return opt;
                }
            }

            // Create new option if it doesn't exist
            ParameterOption newOption = new ParameterOption();
            newOption.setParameter(parameter);
            newOption.setNameBg(value);
            newOption.setNameEn(value);
            newOption.setOrder(allOptions.size()); // Add at end

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
        syncDocuments();
        log.info("Manual full synchronization completed");
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

    private <T> List<List<T>> partitionList(List<T> list, int chunkSize) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            chunks.add(list.subList(i, Math.min(i + chunkSize, list.size())));
        }
        return chunks;
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

    private DocumentChunkResult processDocumentsChunk(List<DocumentRequestDto> documents) {
        long processed = 0, created = 0, updated = 0, errors = 0;
        long chunkStartTime = System.currentTimeMillis();

        for (DocumentRequestDto extDocument : documents) {
            try {
                Optional<Product> productOpt = productRepository.findByExternalId(extDocument.getProductId());

                if (productOpt.isEmpty()) {
                    log.warn("Product not found for document {} with productId: {}",
                            extDocument.getId(), extDocument.getProductId());
                    errors++;
                    continue;
                }

                Product product = productOpt.get();

                Optional<ProductDocument> existingDocument = productDocumentRepository
                        .findByProductIdAndDocumentUrl(product.getId(), extDocument.getDocumentUrl());

                if (existingDocument.isPresent()) {
                    updateDocumentFromExternal(existingDocument.get(), extDocument);
                    updated++;
                } else {
                    createDocumentFromExternal(extDocument, product);
                    created++;
                }

                processed++;

                if (processed % 20 == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }

                if ((System.currentTimeMillis() - chunkStartTime) > (maxChunkDurationMinutes * 60 * 1000)) {
                    log.warn("Document chunk processing taking too long, will continue in next chunk");
                    break;
                }

            } catch (Exception e) {
                errors++;
                log.error("Error processing document {}: {}", extDocument.getId(), e.getMessage());
            }
        }

        entityManager.flush();
        entityManager.clear();

        return new DocumentChunkResult(processed, created, updated, errors);
    }

    private void createDocumentFromExternal(DocumentRequestDto extDocument, Product product) {
        ProductDocument document = new ProductDocument();
        document.setProduct(product);
        updateDocumentFieldsFromExternal(document, extDocument);

        try {
            productDocumentRepository.save(document);
            log.debug("Created document for product: {}", product.getExternalId());
        } catch (Exception e) {
            log.error("Failed to create document for product {}: {}",
                    product.getExternalId(), e.getMessage());
            throw e;
        }
    }

    private void updateDocumentFromExternal(ProductDocument document, DocumentRequestDto extDocument) {
        updateDocumentFieldsFromExternal(document, extDocument);

        try {
            productDocumentRepository.save(document);
            log.debug("Updated document for product: {}", document.getProduct().getExternalId());
        } catch (Exception e) {
            log.error("Failed to update document for product {}: {}",
                    document.getProduct().getExternalId(), e.getMessage());
            throw e;
        }
    }

    private void updateDocumentFieldsFromExternal(ProductDocument document, DocumentRequestDto extDocument) {
        document.setDocumentUrl(extDocument.getDocumentUrl());

        if (extDocument.getComment() != null) {
            extDocument.getComment().forEach(comment -> {
                switch (comment.getLanguageCode()) {
                    case "bg" -> document.setCommentBg(comment.getText());
                    case "en" -> document.setCommentEn(comment.getText());
                }
            });
        }
    }

    private static class DocumentChunkResult {
        long processed, created, updated, errors;

        DocumentChunkResult(long processed, long created, long updated, long errors) {
            this.processed = processed;
            this.created = created;
            this.updated = updated;
            this.errors = errors;
        }
    }

    public void syncDocumentsByProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        log.info("Starting document sync for product: {}", productId);

        try {
            List<DocumentRequestDto> documents = valiApiService.getDocumentsByProduct(product.getExternalId());

            long created = 0, updated = 0, errors = 0;

            for (DocumentRequestDto extDocument : documents) {
                try {
                    Optional<ProductDocument> existingDocument = productDocumentRepository
                            .findByProductIdAndDocumentUrl(product.getId(), extDocument.getDocumentUrl());

                    if (existingDocument.isPresent()) {
                        updateDocumentFromExternal(existingDocument.get(), extDocument);
                        updated++;
                    } else {
                        createDocumentFromExternal(extDocument, product);
                        created++;
                    }
                } catch (Exception e) {
                    errors++;
                    log.error("Error processing document {} for product {}: {}",
                            extDocument.getId(), productId, e.getMessage());
                }
            }

            log.info("Document sync completed for product {} - Created: {}, Updated: {}, Errors: {}",
                    productId, created, updated, errors);

        } catch (Exception e) {
            log.error("Error syncing documents for product {}: {}", productId, e.getMessage());
            throw e;
        }
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

    private String generateSlug(String nameEn, String nameBg) {
        // Try nameEn first
        String name = nameEn;

        // If nameEn is null or empty, use nameBg
        if (name == null || name.trim().isEmpty()) {
            name = nameBg;
        }

        // If both are null or empty, return null
        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        return createSlugFromName(name);
    }

    // Alternative method if you want to pass the whole Product entity
    private String generateSlug(Product product) {
        if (product == null) {
            return null;
        }

        return generateSlug(product.getNameEn(), product.getNameBg());
    }

    // The actual slug creation logic
    private String createSlugFromName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        // First convert Cyrillic to Latin
        String transliterated = transliterateCyrillic(name.trim());

        // Then apply the slug logic
        return transliterated.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    // Static mapping for better performance
    private static final Map<String, String> CYRILLIC_TO_LATIN = Map.ofEntries(
            // Uppercase
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

            // Lowercase
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

    // Debug method to check what's happening
    private void debugSlugGeneration(Product product) {
        System.out.println("=== DEBUG SLUG GENERATION ===");
        System.out.println("nameEn: '" + product.getNameEn() + "'");
        System.out.println("nameBg: '" + product.getNameBg() + "'");
        System.out.println("nameEn is null: " + (product.getNameEn() == null));
        System.out.println("nameEn is empty: " + (product.getNameEn() != null && product.getNameEn().trim().isEmpty()));
        System.out.println("Generated slug: '" + generateSlug(product) + "'");
        System.out.println("============================");
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
        category.setSlug(generateSlug(category.getNameEn(), category.getNameBg()));

        return category;
    }

    private void updateCategoryFromExternal(Category category, CategoryRequestFromExternalDto extCategory) {
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

        if (category.getSlug() == null || category.getSlug().isEmpty()) {
            category.setSlug(generateSlug(category.getNameEn(), category.getNameBg()));
        }
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

    private static class ParameterMappingResult {
        final int linked;
        final int created;

        ParameterMappingResult(int linked, int created) {
            this.linked = linked;
            this.created = created;
        }
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        String str = value.toString().trim();
        return str.isEmpty() ? null : str;
    }
}