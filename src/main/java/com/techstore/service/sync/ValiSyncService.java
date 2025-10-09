package com.techstore.service.sync;

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
import com.techstore.repository.CategoryRepository;
import com.techstore.repository.ManufacturerRepository;
import com.techstore.repository.ParameterOptionRepository;
import com.techstore.repository.ParameterRepository;
import com.techstore.repository.ProductRepository;
import com.techstore.service.CachedLookupService;
import com.techstore.service.ValiApiService;
import com.techstore.util.LogHelper;
import com.techstore.util.SyncHelper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.techstore.util.LogHelper.LOG_STATUS_FAILED;
import static com.techstore.util.LogHelper.LOG_STATUS_SUCCESS;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValiSyncService {

    private final ValiApiService valiApiService;
    private final CategoryRepository categoryRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final ProductRepository productRepository;
    private final ParameterRepository parameterRepository;
    private final ParameterOptionRepository parameterOptionRepository;
    private final EntityManager entityManager;
    private final CachedLookupService cachedLookupService;
    private final SyncHelper syncHelper;
    private final LogHelper logHelper;

    @Value("#{'${excluded.categories.external-ids}'.split(',')}")
    private Set<Long> excludedCategories;

    @Value("${app.sync.batch-size:30}")
    private int batchSize;

    @Value("${app.sync.max-chunk-duration-minutes:5}")
    private int maxChunkDurationMinutes;

    // ===========================================
    // MANUFACTURERS SYNC
    // ===========================================

    @Transactional
    public void syncManufacturers() {
        String syncType = "MANUFACTURERS";
        SyncLog syncLog = logHelper.createSyncLogSimple(syncType);
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
            }

            logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS,
                    (long) externalManufacturers.size(), created, updated, 0, null, startTime);
            log.info("Manufacturers synchronization completed - Created: {}, Updated: {}", created, updated);

        } catch (Exception e) {
            logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, e.getMessage(), startTime);
            log.error("Error during manufacturers synchronization", e);
            throw new RuntimeException(e);
        }
    }

    // ===========================================
    // CATEGORIES SYNC
    // ===========================================

    @Transactional
    public void syncCategories() {
        String syncType = "CATEGORIES";
        SyncLog syncLog = logHelper.createSyncLogSimple(syncType);
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

            // Update parent relationships after all categories are saved
            updateCategoryParents(externalCategories, existingCategories);

            logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, externalCategories.size(),
                    created, updated, 0,
                    skipped > 0 ? String.format("Skipped %d excluded categories", skipped) : null,
                    startTime);

        } catch (Exception e) {
            logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, e.getMessage(), startTime);
            throw new RuntimeException(e);
        }
    }

    // ===========================================
    // PARAMETERS SYNC
    // ===========================================

    @Transactional
    public void syncParameters() {
        String syncType = "PARAMETERS";
        SyncLog syncLog = logHelper.createSyncLogSimple(syncType);
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting Vali parameters synchronization with options");

            List<Category> categories = categoryRepository.findAll();
            long totalProcessed = 0, created = 0, updated = 0, errors = 0;

            for (Category category : categories) {
                try {
                    Map<String, Parameter> existingParameters = cachedLookupService.getParametersByCategory(category);

                    List<ParameterRequestDto> externalParameters = valiApiService.getParametersByCategory(category.getExternalId());

                    if (externalParameters == null || externalParameters.isEmpty()) {
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

            logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, totalProcessed, created, updated, errors,
                    errors > 0 ? message : null, startTime);

            log.info("Vali parameters synchronization completed - Processed: {}, Created: {}, Updated: {}, Errors: {}",
                    totalProcessed, created, updated, errors);

        } catch (Exception e) {
            logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, e.getMessage(), startTime);
            log.error("Error during Vali parameters synchronization", e);
            throw new RuntimeException(e);
        }
    }

    // ===========================================
    // PRODUCTS SYNC
    // ===========================================

    @Transactional
    public void syncProducts() {
        String syncType = "PRODUCTS";
        log.info("Starting chunked products synchronization");
        SyncLog syncLog = logHelper.createSyncLogSimple(syncType);
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

            logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, totalProcessed, created, updated, errors,
                    errors > 0 ? String.format("Completed with %d errors", errors) : null, startTime);
            log.info("Products synchronization completed - Created: {}, Updated: {}, Errors: {}", created, updated, errors);

        } catch (Exception e) {
            logHelper.updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, totalProcessed, created, updated, errors, e.getMessage(), startTime);
            log.error("Error during products synchronization", e);
            throw new RuntimeException(e);
        }
    }

    // ===========================================
    // PARAMETER OPTIONS SYNC
    // ===========================================

    private void syncValiParameterOptions(Parameter parameter, List<ParameterOptionRequestDto> externalOptions) {
        if (externalOptions == null || externalOptions.isEmpty()) {
            return;
        }

        Map<String, ParameterOption> existingOptions = parameterOptionRepository
                .findByParameterIdOrderByOrderAsc(parameter.getId())
                .stream()
                .filter(opt -> opt.getNameBg() != null && !opt.getNameBg().isEmpty())
                .collect(Collectors.toMap(
                        ParameterOption::getNameBg,
                        o -> o,
                        (existing, duplicate) -> {
                            log.warn("Duplicate parameter option name '{}' for parameter {}, keeping first (IDs: {} and {})",
                                    existing.getNameBg(), parameter.getNameBg(), existing.getId(), duplicate.getId());
                            return existing;
                        }
                ));

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

    // ===========================================
    // PRODUCT PARAMETERS MAPPING
    // ===========================================

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

    // ===========================================
    // CATEGORY HELPERS
    // ===========================================

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

        String baseSlug = syncHelper.createSlugFromName(categoryName);

        if (!slugExistsInDatabaseForVali(baseSlug, category.getId())) {
            return baseSlug;
        }

        String discriminator = syncHelper.extractDiscriminator(categoryName);
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
        // Set parent references without saving - Hibernate will handle this on transaction commit
        for (CategoryRequestFromExternalDto extCategory : externalCategories) {
            if (extCategory.getParent() != null && extCategory.getParent() != 0) {
                Category category = existingCategories.get(extCategory.getId());
                Category parent = existingCategories.get(extCategory.getParent());

                if (category != null && parent != null && !parent.equals(category)) {
                    category.setParent(parent);
                }
            }
        }
    }

    // ===========================================
    // MANUFACTURER HELPERS
    // ===========================================

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

    // ===========================================
    // PARAMETER HELPERS
    // ===========================================

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

    // ===========================================
    // PRODUCT SYNC BY CATEGORY
    // ===========================================

    private CategorySyncResult syncProductsByCategory(Category category) {
        long totalProcessed = 0, created = 0, updated = 0, errors = 0;

        try {
            Map<Long, Manufacturer> manufacturersMap = manufacturerRepository.findAll()
                    .stream()
                    .filter(m -> m.getExternalId() != null)
                    .collect(Collectors.toMap(
                            Manufacturer::getExternalId,
                            m -> m,
                            (existing, duplicate) -> {
                                log.warn("Duplicate manufacturer externalId: {}, IDs: {} and {}, keeping first",
                                        existing.getExternalId(), existing.getId(), duplicate.getId());
                                return existing;
                            }
                    ));

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
                        entityManager.flush();
                        entityManager.clear();
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

    // ===========================================
    // PRODUCT HELPERS
    // ===========================================

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
        if (extProduct.getCategories() == null || extProduct.getCategories().isEmpty()) {
            return;
        }

        Long categoryId = extProduct.getCategories().get(0).getId();
        Optional<Category> categoryOpt = categoryRepository.findByExternalId(categoryId);

        if (categoryOpt.isPresent()) {
            product.setCategory(categoryOpt.get());
        } else {
            log.warn("Category with external ID {} not found for product {}",
                    categoryId, extProduct.getReferenceNumber());
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

    // ===========================================
    // UTILITY METHODS
    // ===========================================

    private <T> List<List<T>> partitionList(List<T> list, int partitionSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += partitionSize) {
            partitions.add(list.subList(i, Math.min(i + partitionSize, list.size())));
        }
        return partitions;
    }

    // ===========================================
    // RESULT CLASSES
    // ===========================================

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