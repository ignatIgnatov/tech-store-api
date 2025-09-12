package com.techstore.service;

import com.techstore.dto.external.ExternalCategoryDto;
import com.techstore.dto.external.ExternalManufacturerDto;
import com.techstore.dto.external.ExternalParameterDto;
import com.techstore.dto.external.ExternalParameterOptionDto;
import com.techstore.dto.external.ExternalProductDto;
import com.techstore.dto.external.PaginatedProductsDto;
import com.techstore.entity.Category;
import com.techstore.entity.Manufacturer;
import com.techstore.entity.Parameter;
import com.techstore.entity.ParameterOption;
import com.techstore.entity.Product;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncService {

    private final ValiApiService valiApiService;
    private final CategoryRepository categoryRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final ProductRepository productRepository;
    private final ParameterRepository parameterRepository;
    private final ParameterOptionRepository parameterOptionRepository;
    private final SyncLogRepository syncLogRepository;
    private final EntityManager entityManager;

    @Value("${app.sync.enabled}")
    private boolean syncEnabled;

    @Value("${app.sync.batch-size}")
    private int batchSize;

    @Scheduled(cron = "${app.sync.cron}")
    @Async
    public void scheduledSync() {
        if (!syncEnabled) {
            log.info("Synchronization is disabled");
            return;
        }

        log.info("Starting scheduled synchronization");
        try {
            syncCategories();
            log.info("Scheduled category synchronization completed successfully at " + LocalDateTime.now());
            syncManufacturers();
            log.info("Scheduled manufacturers synchronization completed successfully at " + LocalDateTime.now());
            syncParameters();
            log.info("Scheduled parameters synchronization completed successfully at" + LocalDateTime.now());
            syncProducts();
            log.info("Scheduled products synchronization completed successfully at " + LocalDateTime.now());
        } catch (Exception e) {
            log.error("Error during scheduled synchronization", e);
        }
    }

    @Transactional
    public void syncCategories() {
        SyncLog syncLog = createSyncLog("CATEGORIES");
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting categories synchronization");

            List<ExternalCategoryDto> externalCategories = valiApiService.getCategories();
            Map<Long, Category> existingCategories = categoryRepository.findAll()
                    .stream()
                    .collect(Collectors.toMap(Category::getExternalId, c -> c));

            long created = 0, updated = 0;

            for (ExternalCategoryDto extCategory : externalCategories) {
                Category category = existingCategories.get(extCategory.getId());

                if (category == null) {
                    category = createCategoryFromExternal(extCategory);
                    created++;
                } else {
                    updateCategoryFromExternal(category, extCategory);
                    updated++;
                }

                categoryRepository.save(category);
            }

            // Update parent relationships in a second pass
            updateCategoryParents(externalCategories, existingCategories);

            syncLog.setStatus("SUCCESS");
            syncLog.setRecordsProcessed((long) externalCategories.size());
            syncLog.setRecordsCreated(created);
            syncLog.setRecordsUpdated(updated);

            log.info("Categories synchronization completed - Created: {}, Updated: {}", created, updated);

        } catch (Exception e) {
            syncLog.setStatus("FAILED");
            syncLog.setErrorMessage(e.getMessage());
            log.error("Error during categories synchronization", e);
            throw e;
        } finally {
            syncLog.setDurationMs(System.currentTimeMillis() - startTime);
            syncLogRepository.save(syncLog);
        }
    }

    @Transactional
    public void syncManufacturers() {
        SyncLog syncLog = createSyncLog("MANUFACTURERS");
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting manufacturers synchronization");

            List<ExternalManufacturerDto> externalManufacturers = valiApiService.getManufacturers();
            Map<Long, Manufacturer> existingManufacturers = manufacturerRepository.findAll()
                    .stream()
                    .collect(Collectors.toMap(Manufacturer::getExternalId, m -> m));

            long created = 0, updated = 0;

            for (ExternalManufacturerDto extManufacturer : externalManufacturers) {
                Manufacturer manufacturer = existingManufacturers.get(extManufacturer.getId());

                if (manufacturer == null) {
                    manufacturer = createManufacturerFromExternal(extManufacturer);
                    created++;
                } else {
                    updateManufacturerFromExternal(manufacturer, extManufacturer);
                    updated++;
                }

                manufacturerRepository.save(manufacturer);
            }

            syncLog.setStatus("SUCCESS");
            syncLog.setRecordsProcessed((long) externalManufacturers.size());
            syncLog.setRecordsCreated(created);
            syncLog.setRecordsUpdated(updated);

            log.info("Manufacturers synchronization completed - Created: {}, Updated: {}", created, updated);

        } catch (Exception e) {
            syncLog.setStatus("FAILED");
            syncLog.setErrorMessage(e.getMessage());
            log.error("Error during manufacturers synchronization", e);
            throw e;
        } finally {
            syncLog.setDurationMs(System.currentTimeMillis() - startTime);
            syncLogRepository.save(syncLog);
        }
    }

    @Transactional
    public void syncParameters() {
        SyncLog syncLog = createSyncLog("PARAMETERS");
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting parameters synchronization");

            List<Category> categories = categoryRepository.findAll();
            long totalProcessed = 0, created = 0, updated = 0;

            for (Category category : categories) {
                List<ExternalParameterDto> externalParameters = valiApiService.getParametersByCategory(category.getExternalId());

                // Вземаме съществуващите параметри за категорията
                Map<Long, Parameter> existingParameters = parameterRepository
                        .findByCategoryIdOrderByOrderAsc(category.getId())
                        .stream()
                        .collect(Collectors.toMap(Parameter::getExternalId, p -> p));

                for (ExternalParameterDto extParameter : externalParameters) {
                    Parameter parameter = existingParameters.get(extParameter.getId());

                    if (parameter == null) {
                        // Нов параметър – създаваме и merge-ваме
                        parameter = createParameterFromExternal(extParameter, category);
                        parameter = parameterRepository.save(parameter);
                        created++;
                    } else {
                        // Съществуващ – обновяваме
                        updateParameterFromExternal(parameter, extParameter);
                        parameter = parameterRepository.save(parameter);
                        updated++;
                    }

                    // Синхронизация на опциите
                    syncParameterOptions(parameter, extParameter.getOptions());
                }

                totalProcessed += externalParameters.size();
            }

            syncLog.setStatus("SUCCESS");
            syncLog.setRecordsProcessed(totalProcessed);
            syncLog.setRecordsCreated(created);
            syncLog.setRecordsUpdated(updated);

            log.info("Parameters synchronization completed - Created: {}, Updated: {}", created, updated);

        } catch (Exception e) {
            syncLog.setStatus("FAILED");
            syncLog.setErrorMessage(e.getMessage());
            log.error("Error during parameters synchronization", e);
            throw e;
        } finally {
            syncLog.setDurationMs(System.currentTimeMillis() - startTime);
            syncLogRepository.save(syncLog);
        }
    }

    private void syncParameterOptions(Parameter parameter, List<ExternalParameterOptionDto> externalOptions) {
        Map<Long, ParameterOption> existingOptions = parameterOptionRepository
                .findByParameterIdOrderByOrderAsc(parameter.getId())
                .stream()
                .collect(Collectors.toMap(ParameterOption::getExternalId, o -> o));

        for (ExternalParameterOptionDto extOption : externalOptions) {
            ParameterOption option = existingOptions.get(extOption.getId());

            if (option == null) {
                // Нови опции – създаваме и merge-ваме
                option = createParameterOptionFromExternal(extOption, parameter);
                parameterOptionRepository.save(option);
            } else {
                // Съществуващи – обновяваме
                updateParameterOptionFromExternal(option, extOption);
                parameterOptionRepository.save(option);
            }
        }
    }

    private SyncLog createSyncLog(String syncType) {
        SyncLog syncLog = new SyncLog();
        syncLog.setSyncType(syncType);
        syncLog.setStatus("IN_PROGRESS");
        return syncLogRepository.save(syncLog);
    }

    private Category createCategoryFromExternal(ExternalCategoryDto extCategory) {
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
        category.setSlug(generateSlug(baseName));

        return category;
    }


    private void updateCategoryFromExternal(Category category, ExternalCategoryDto extCategory) {
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
            String baseName = category.getNameEn() != null ? category.getNameEn() : category.getNameBg();
            category.setSlug(generateSlug(baseName));
        }
    }


    private void updateCategoryParents(List<ExternalCategoryDto> externalCategories, Map<Long, Category> existingCategories) {
        for (ExternalCategoryDto extCategory : externalCategories) {
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

    private Manufacturer createManufacturerFromExternal(ExternalManufacturerDto extManufacturer) {
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

    private void updateManufacturerFromExternal(Manufacturer manufacturer, ExternalManufacturerDto extManufacturer) {
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

    private Parameter createParameterFromExternal(ExternalParameterDto extParameter, Category category) {
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

    private void updateParameterFromExternal(Parameter parameter, ExternalParameterDto extParameter) {
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

    private ParameterOption createParameterOptionFromExternal(ExternalParameterOptionDto extOption, Parameter parameter) {
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
    }

    private void updateParameterOptionFromExternal(ParameterOption option, ExternalParameterOptionDto extOption) {
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
    }

    @Transactional
    public void syncProductsByCategory(Long id) {
        categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));

        valiApiService.getProductsByCategory(id);
    }

    public void syncProducts() {
        SyncLog syncLog = createSyncLog("PRODUCTS");
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting products synchronization");

            long totalProcessed = 0, created = 0, updated = 0, errors = 0;

            // Prepare manufacturers map
            Map<Long, Manufacturer> manufacturersMap = manufacturerRepository.findAll()
                    .stream()
                    .collect(Collectors.toMap(Manufacturer::getExternalId, m -> m));

            // Get all categories for iteration
            List<Category> categories = categoryRepository.findAll();
            log.info("Found {} categories to process", categories.size());

            for (Category category : categories) {
                try {
                    // Process each category in its own transaction
                    CategorySyncResult result = processCategoryInTransaction(category, manufacturersMap);
                    totalProcessed += result.processed;
                    created += result.created;
                    updated += result.updated;
                    errors += result.errors;

                } catch (Exception e) {
                    log.error("Error processing category {}: {}", category.getExternalId(), e.getMessage());
                    errors++;
                }
            }

            syncLog.setStatus("SUCCESS");
            syncLog.setRecordsProcessed(totalProcessed);
            syncLog.setRecordsCreated(created);
            syncLog.setRecordsUpdated(updated);

            if (errors > 0) {
                syncLog.setErrorMessage(String.format("Completed with %d errors", errors));
            }

            log.info("Products synchronization completed - Created: {}, Updated: {}, Errors: {}",
                    created, updated, errors);

        } catch (Exception e) {
            syncLog.setStatus("FAILED");
            syncLog.setErrorMessage(e.getMessage());
            log.error("Error during products synchronization", e);
            throw e;
        } finally {
            syncLog.setDurationMs(System.currentTimeMillis() - startTime);
            syncLogRepository.save(syncLog);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CategorySyncResult processCategoryInTransaction(Category category, Map<Long, Manufacturer> manufacturersMap) {
        long processed = 0, created = 0, updated = 0, errors = 0;

        log.debug("Syncing products for category: {} (ID: {})",
                category.getNameEn() != null ? category.getNameEn() : category.getNameBg(),
                category.getExternalId());

        // Fetch products for this category
        List<ExternalProductDto> categoryProducts = valiApiService.getProductsByCategory(category.getExternalId());

        if (categoryProducts.isEmpty()) {
            log.debug("No products found for category {}", category.getExternalId());
            return new CategorySyncResult(0, 0, 0, 0);
        }

        for (ExternalProductDto extProduct : categoryProducts) {
            try {
                Optional<Product> existingProduct = productRepository.findByExternalIdWithSessionClear(extProduct.getId());

                if (existingProduct.isPresent()) {
                    updateProductFromExternal(existingProduct.get(), extProduct, manufacturersMap);
                    updated++;
                } else {
                    createProductFromExternal(extProduct, manufacturersMap);
                    created++;
                }

                processed++;

                // Clear session periodically
                if (processed % 50 == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }

            } catch (Exception e) {
                errors++;
                log.error("Error processing product {} from category {}: {}",
                        extProduct.getId(), category.getExternalId(), e.getMessage());
                entityManager.clear();
            }
        }

        return new CategorySyncResult(processed, created, updated, errors);
    }

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


    private void createProductFromExternal(ExternalProductDto extProduct, Map<Long, Manufacturer> manufacturersMap) {
        Manufacturer manufacturer = manufacturersMap.get(extProduct.getManufacturerId());

        // CHANGE: Don't return early if manufacturer is null, just log a warning
        if (manufacturer == null) {
            log.warn("Manufacturer not found for product {}: {}. Creating product without manufacturer.",
                    extProduct.getId(), extProduct.getManufacturerId());
            // Continue without manufacturer - set it to null
            manufacturer = null;
        }

        Product product = new Product();
        product.setId(null);
        updateProductFieldsFromExternal(product, extProduct, manufacturer);

        // Retry logic for database operations
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                product = productRepository.save(product);
                log.debug("Created product with externalId: {}", extProduct.getId());
                return;
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    log.error("Failed to create product with externalId {} after {} attempts: {}",
                            extProduct.getId(), maxRetries, e.getMessage());
                    throw e;
                }
                log.warn("Attempt {} failed for product {}, retrying...", attempt, extProduct.getId());
                try {
                    Thread.sleep(1000 * attempt); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
            }
        }
    }

    private void updateProductFromExternal(Product product, ExternalProductDto extProduct, Map<Long, Manufacturer> manufacturersMap) {
        Manufacturer manufacturer = manufacturersMap.get(extProduct.getManufacturerId());

        // CHANGE: Don't return early if manufacturer is null, just log a warning
        if (manufacturer == null) {
            log.warn("Manufacturer not found for product {}: {}. Updating product without manufacturer.",
                    extProduct.getId(), extProduct.getManufacturerId());
            // Continue without manufacturer - set it to null
            manufacturer = null;
        }

        updateProductFieldsFromExternal(product, extProduct, manufacturer);

        try {
            productRepository.save(product);
            log.debug("Updated product with externalId: {}", extProduct.getId());
        } catch (Exception e) {
            log.error("Failed to update product with externalId {}: {}", extProduct.getId(), e.getMessage());
            throw e;
        }
    }

    private void updateProductFieldsFromExternal(Product product, ExternalProductDto extProduct, Manufacturer manufacturer) {
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
        product.setWarrantyMonths(extProduct.getWarranty());
        product.setWeight(extProduct.getWeight());

        // Set names and descriptions
        if (extProduct.getName() != null) {
            extProduct.getName().forEach(name -> {
                if ("bg".equals(name.getLanguageCode())) {
                    product.setNameBg(name.getText());
                } else if ("en".equals(name.getLanguageCode())) {
                    product.setNameEn(name.getText());
                }
            });
        }

        if (extProduct.getDescription() != null) {
            extProduct.getDescription().forEach(desc -> {
                if ("bg".equals(desc.getLanguageCode())) {
                    product.setDescriptionBg(desc.getText());
                } else if ("en".equals(desc.getLanguageCode())) {
                    product.setDescriptionEn(desc.getText());
                }
            });
        }

        // Calculate final price
        product.calculateFinalPrice();
    }

    private void createProductRelatedEntities(Product product, ExternalProductDto extProduct) {
        // This method would create categories, parameters, images, documents, and flags
        // Implementation would involve complex entity relationships
        // For brevity, showing the structure but not full implementation
        log.debug("Creating related entities for product: {}", product.getId());
    }

    private void updateProductRelatedEntities(Product product, ExternalProductDto extProduct) {
        // This method would update categories, parameters, images, documents, and flags
        // Implementation would involve complex entity relationships
        // For brevity, showing the structure but not full implementation
        log.debug("Updating related entities for product: {}", product.getId());
    }

    private String generateSlug(String name) {
        return name == null ? null :
                name.toLowerCase()
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("^-|-$", "");
    }
}