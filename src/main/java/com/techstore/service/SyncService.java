package com.techstore.service;

import com.techstore.dto.external.ImageDto;
import com.techstore.dto.request.CategoryRequestFromExternalDto;
import com.techstore.dto.request.DocumentRequestDto;
import com.techstore.dto.request.ManufacturerRequestDto;
import com.techstore.dto.request.ParameterOptionRequestDto;
import com.techstore.dto.request.ParameterRequestDto;
import com.techstore.dto.request.ProductRequestDto;
import com.techstore.dto.tekra.TekraSyncResponse;
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

    // ============ CATEGORIES SYNC ============
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

    // ============ MANUFACTURERS SYNC ============
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

    // ============ PARAMETERS SYNC ============
    @Transactional
    public void syncParameters() {
        SyncLog syncLog = createSyncLogSimple("PARAMETERS");
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting chunked parameters synchronization");

            List<Category> categories = categoryRepository.findAll();
            long totalProcessed = 0, created = 0, updated = 0, errors = 0;

            for (Category category : categories) {
                try {
                    Map<String, Parameter> existingParameters = cachedLookupService.getParametersByCategory(category);

                    List<ParameterRequestDto> externalParameters = valiApiService.getParametersByCategory(category.getExternalId());

                    List<Parameter> toSave = new ArrayList<>();

                    for (ParameterRequestDto extParam : externalParameters) {
                        Parameter parameter = existingParameters.get(extParam.getId());
                        if (parameter == null) {
                            parameter = parameterRepository
                                    .findByExternalIdAndCategoryId(extParam.getId(), category.getId())
                                    .orElseGet(() -> createParameterFromExternal(extParam, category));
                            created++;
                        } else {
                            updateParameterFromExternal(parameter, extParam);
                            updated++;
                        }

                        toSave.add(parameter);
                        existingParameters.put(String.valueOf(parameter.getExternalId()), parameter); // Обновяване на кеша
                    }

                    if (!toSave.isEmpty()) {
                        parameterRepository.saveAll(toSave);
                    }

                    totalProcessed += externalParameters.size();

                } catch (Exception e) {
                    log.error("Error syncing parameters for category {}: {}", category.getExternalId(), e.getMessage());
                    errors++;
                }
            }

            updateSyncLogSimple(syncLog, LOG_STATUS_SUCCESS, totalProcessed, created, updated, errors,
                    errors > 0 ? String.format("Completed with %d errors", errors) : null, startTime);

            log.info("Parameters synchronization completed - Processed: {}, Created: {}, Updated: {}, Errors: {}",
                    totalProcessed, created, updated, errors);

        } catch (Exception e) {
            updateSyncLogSimple(syncLog, LOG_STATUS_FAILED, 0, 0, 0, 0, e.getMessage(), startTime);
            log.error("Error during parameters synchronization", e);
            throw e;
        }
    }

    // ============ PRODUCTS SYNC ============
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
    public TekraSyncResult syncTekraCategories() {
        log.info("Starting Tekra categories synchronization (fixed version)");
        long processed = 0, created = 0, updated = 0, errors = 0;

        try {
            List<Map<String, Object>> rawCategories = tekraApiService.getCategoriesRaw();

            if (rawCategories.isEmpty()) {
                log.warn("No categories returned from Tekra API");
                return new TekraSyncResult(0, 0, 0, 0);
            }

            log.info("Processing {} raw categories from Tekra", rawCategories.size());

            Map<String, Category> existingCategories = categoryRepository.findAll()
                    .stream()
                    .filter(cat -> cat.getTekraSlug() != null)
                    .collect(Collectors.toMap(Category::getTekraSlug, cat -> cat));

            for (Map<String, Object> rawCategory : rawCategories) {
                try {
                    // Extract data using Tekra's field names
                    String tekraId = getString(rawCategory, "id");
                    String slug = getString(rawCategory, "slug");
                    String name = getString(rawCategory, "name");

                    if (tekraId == null || name == null) {
                        log.warn("Skipping category with missing required fields: id={}, name={}", tekraId, name);
                        errors++;
                        continue;
                    }

                    // Use slug as key for existing categories lookup
                    String categoryKey = slug != null ? slug : tekraId;

                    Category category = existingCategories.get(categoryKey);

                    if (category == null) {
                        // Create new category
                        category = createCategoryFromTekraData(rawCategory);
                        if (category != null) {
                            category = categoryRepository.save(category);
                            existingCategories.put(category.getTekraSlug(), category);
                            created++;
                            log.debug("Created category: {} ({})", category.getNameBg(), category.getTekraSlug());
                        } else {
                            errors++;
                        }
                    } else {
                        // Update existing category
                        updateCategoryFromTekraData(category, rawCategory);
                        category = categoryRepository.save(category);
                        updated++;
                        log.debug("Updated category: {} ({})", category.getNameBg(), category.getTekraSlug());
                    }

                    processed++;

                } catch (Exception e) {
                    errors++;
                    log.error("Error processing Tekra category: {}", e.getMessage(), e);
                }
            }

            log.info("Tekra categories sync completed (fixed) - Processed: {}, Created: {}, Updated: {}, Errors: {}",
                    processed, created, updated, errors);

            return new TekraSyncResult(processed, created, updated, errors);

        } catch (Exception e) {
            log.error("Error during Tekra categories synchronization (fixed)", e);
            throw e;
        }
    }

    private Category createCategoryFromTekraData(Map<String, Object> rawData) {
        try {
            Category category = new Category();

            category.setTekraId(getString(rawData, "id"));
            category.setTekraSlug(getString(rawData, "slug"));

            // Tekra returns Bulgarian names, set both BG and EN
            String name = getString(rawData, "name");
            category.setNameBg(name);
            category.setNameEn(name); // You might want to translate this later

            String slug = getString(rawData, "slug");
            if (slug == null || slug.isEmpty()) {
                slug = generateSlug(name);
            }
            category.setSlug(slug);

            // Tekra categories seem to be always active if returned by API
            category.setShow(true);

            // Try to get count for sort order
            String countStr = getString(rawData, "count");
            if (countStr != null) {
                try {
                    Integer count = Integer.parseInt(countStr);
                    category.setSortOrder(count); // Use product count as sort order
                } catch (NumberFormatException e) {
                    category.setSortOrder(0);
                }
            } else {
                category.setSortOrder(0);
            }

            return category;
        } catch (Exception e) {
            log.error("Error creating category from Tekra data", e);
            return null;
        }
    }

    private void updateCategoryFromTekraData(Category category, Map<String, Object> rawData) {
        try {
            String name = getString(rawData, "name");
            if (name != null) {
                category.setNameBg(name);
                category.setNameEn(name);
            }

            // Update sort order based on product count
            String countStr = getString(rawData, "count");
            if (countStr != null) {
                try {
                    Integer count = Integer.parseInt(countStr);
                    category.setSortOrder(count);
                } catch (NumberFormatException e) {
                    // Keep existing sort order
                }
            }

            // Always active if returned by API
            category.setShow(true);

        } catch (Exception e) {
            log.error("Error updating category from Tekra data", e);
        }
    }

    /**
     * Fixed Tekra products synchronization using XML parsing
     */
    @Transactional
    public TekraSyncResult syncTekraProducts() {
        log.info("Starting Tekra products synchronization (XML parsing)");
        long totalProcessed = 0, totalCreated = 0, totalUpdated = 0, totalErrors = 0;

        try {
            // Test with video surveillance category first
            String testCategory = "videonablyudenie";

            TekraSyncResult result = syncTekraProductsByCategoryXML(testCategory);
            totalProcessed += result.processed;
            totalCreated += result.created;
            totalUpdated += result.updated;
            totalErrors += result.errors;

            log.info("Tekra products sync completed (XML) - Processed: {}, Created: {}, Updated: {}, Errors: {}",
                    totalProcessed, totalCreated, totalUpdated, totalErrors);

            return new TekraSyncResult(totalProcessed, totalCreated, totalUpdated, totalErrors);

        } catch (Exception e) {
            log.error("Error during Tekra products synchronization (XML)", e);
            throw e;
        }
    }

    private TekraSyncResult syncTekraProductsByCategoryXML(String categorySlug) {
        long processed = 0, created = 0, updated = 0, errors = 0;

        try {
            log.info("Syncing products for category: {} (XML parsing)", categorySlug);

            List<Map<String, Object>> rawProducts = tekraApiService.getProductsRaw(categorySlug);

            if (rawProducts.isEmpty()) {
                log.warn("No products returned from Tekra API for category: {}", categorySlug);
                return new TekraSyncResult(0, 0, 0, 0);
            }

            log.info("Processing {} products from Tekra XML for category: {}", rawProducts.size(), categorySlug);

            // Log first product structure for debugging
            if (!rawProducts.isEmpty()) {
                Map<String, Object> firstProduct = rawProducts.get(0);
                log.info("First product XML fields: {}", firstProduct.keySet());
                log.info("First product sample: name={}, sku={}, price={}, manufacturer={}",
                        firstProduct.get("name"), firstProduct.get("sku"), firstProduct.get("price"), firstProduct.get("manufacturer"));
            }

            for (Map<String, Object> rawProduct : rawProducts) {
                try {
                    String sku = getStringValue(rawProduct, "sku");
                    String name = getStringValue(rawProduct, "name");

                    if (sku == null || name == null) {
                        log.warn("Skipping product with missing required fields: sku={}, name={}", sku, name);
                        errors++;
                        continue;
                    }

                    // Use SKU as the unique identifier for Tekra products
                    Optional<Product> existingProduct = productRepository.findBySku(sku);

                    if (existingProduct.isPresent()) {
                        updateProductFromTekraXML(existingProduct.get(), rawProduct, categorySlug);
                        updated++;
                        log.debug("Updated product: {} ({})", name, sku);
                    } else {
                        Product newProduct = createProductFromTekraXML(rawProduct, categorySlug);
                        if (newProduct != null) {
                            created++;
                            log.debug("Created product: {} ({})", name, sku);
                        } else {
                            errors++;
                        }
                    }

                    processed++;

                } catch (Exception e) {
                    errors++;
                    log.error("Error processing Tekra product: {}", e.getMessage(), e);
                }
            }

            log.info("Category {} sync completed (XML) - Processed: {}, Created: {}, Updated: {}, Errors: {}",
                    categorySlug, processed, created, updated, errors);

        } catch (Exception e) {
            log.error("Error getting products for category {}: {}", categorySlug, e.getMessage());
            errors++;
        }

        return new TekraSyncResult(processed, created, updated, errors);
    }

// Helper methods for creating/updating products from Tekra XML data

    private Product createProductFromTekraXML(Map<String, Object> rawData, String categorySlug) {
        try {
            Product product = new Product();
            updateProductFieldsFromTekraXML(product, rawData, categorySlug);

            productRepository.save(product);
            return product;
        } catch (Exception e) {
            log.error("Failed to create product from Tekra XML: {}", e.getMessage(), e);
            return null;
        }
    }

    private void updateProductFromTekraXML(Product product, Map<String, Object> rawData, String categorySlug) {
        try {
            updateProductFieldsFromTekraXML(product, rawData, categorySlug);
            productRepository.save(product);
        } catch (Exception e) {
            log.error("Failed to update product from Tekra XML: {}", e.getMessage(), e);
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

    // Safe helper methods for XML data extraction
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        String str = value.toString().trim();
        return str.isEmpty() ? null : str;
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

    /**
     * Test method for XML-based products sync
     */
    public TekraSyncResponse testTekraProductsSyncXML() {
        TekraSyncResponse response = new TekraSyncResponse();
        long startTime = System.currentTimeMillis();

        try {
            List<Map<String, Object>> products = tekraApiService.getProductsRaw("videonablyudenie");

            response.setSuccess(!products.isEmpty());
            response.setMessage(products.isEmpty() ?
                    "No products found in XML" :
                    "Successfully parsed " + products.size() + " products from XML");
            response.setTotalProcessed((long) products.size());
            response.setDurationMs(System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            response.setSuccess(false);
            response.setMessage("Tekra products XML parsing error: " + e.getMessage());
            response.setDurationMs(System.currentTimeMillis() - startTime);
        }

        return response;
    }

    // Helper method for safe string extraction
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        String str = value.toString().trim();
        return str.isEmpty() ? null : str;
    }

    @Transactional
    public TekraSyncResult syncTekraManufacturers() {
        log.info("Starting Tekra manufacturers synchronization");
        long processed = 0, created = 0, updated = 0, errors = 0;

        try {
            // Get manufacturers from surveillance category (main category)
            Set<String> tekraManufacturers = tekraApiService.extractTekraManufacturersFromProducts("videonablyudenie");

            if (tekraManufacturers.isEmpty()) {
                log.warn("No manufacturers found in Tekra products");
                return new TekraSyncResult(0, 0, 0, 0);
            }

            Map<String, Manufacturer> existingManufacturers = manufacturerRepository.findAll()
                    .stream()
                    .collect(Collectors.toMap(Manufacturer::getName, m -> m));

            for (String manufacturerName : tekraManufacturers) {
                try {
                    Manufacturer manufacturer = existingManufacturers.get(manufacturerName);

                    if (manufacturer == null) {
                        manufacturer = createTekraManufacturer(manufacturerName);
                        if (manufacturer != null) {
                            manufacturer = manufacturerRepository.save(manufacturer);
                            existingManufacturers.put(manufacturer.getName(), manufacturer);
                            created++;
                            log.debug("Created manufacturer: {}", manufacturerName);
                        } else {
                            errors++;
                        }
                    } else {
                        // Manufacturer exists, could update if needed
                        updated++;
                        log.debug("Manufacturer already exists: {}", manufacturerName);
                    }

                    processed++;

                } catch (Exception e) {
                    errors++;
                    log.error("Error processing Tekra manufacturer {}: {}", manufacturerName, e.getMessage());
                }
            }

            log.info("Tekra manufacturers sync completed - Processed: {}, Created: {}, Updated: {}, Errors: {}",
                    processed, created, updated, errors);

            return new TekraSyncResult(processed, created, updated, errors);

        } catch (Exception e) {
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

// ============ TEKRA PARAMETERS SYNC ============

    @Transactional
    public TekraSyncResult syncTekraParameters() {
        log.info("Starting Tekra parameters synchronization");
        long totalProcessed = 0, totalCreated = 0, totalUpdated = 0, totalErrors = 0;

        try {
            // Get the main surveillance category for parameters
            Optional<Category> surveillanceCategory = categoryRepository.findByTekraSlug("videonablyudenie");

            if (surveillanceCategory.isEmpty()) {
                log.error("Surveillance category not found. Sync categories first.");
                return new TekraSyncResult(0, 0, 0, 0);
            }

            Category category = surveillanceCategory.get();

            // Extract parameters from Tekra products
            Map<String, Set<String>> tekraParameters = tekraApiService
                    .extractTekraParametersFromProducts("videonablyudenie");

            if (tekraParameters.isEmpty()) {
                log.warn("No parameters found in Tekra products");
                return new TekraSyncResult(0, 0, 0, 0);
            }

            log.info("Processing {} parameter types from Tekra", tekraParameters.size());

            Map<String, Parameter> existingParameters = cachedLookupService.getParametersByCategory(category);

            for (Map.Entry<String, Set<String>> paramEntry : tekraParameters.entrySet()) {
                try {
                    String parameterKey = paramEntry.getKey();
                    Set<String> parameterValues = paramEntry.getValue();

                    TekraSyncResult paramResult = syncTekraParameter(category, parameterKey, parameterValues, existingParameters);
                    totalProcessed += paramResult.processed;
                    totalCreated += paramResult.created;
                    totalUpdated += paramResult.updated;
                    totalErrors += paramResult.errors;

                } catch (Exception e) {
                    totalErrors++;
                    log.error("Error processing Tekra parameter {}: {}", paramEntry.getKey(), e.getMessage());
                }
            }

            log.info("Tekra parameters sync completed - Processed: {}, Created: {}, Updated: {}, Errors: {}",
                    totalProcessed, totalCreated, totalUpdated, totalErrors);

            return new TekraSyncResult(totalProcessed, totalCreated, totalUpdated, totalErrors);

        } catch (Exception e) {
            log.error("Error during Tekra parameters synchronization", e);
            throw e;
        }
    }

    private TekraSyncResult syncTekraParameter(Category category, String parameterKey, Set<String> parameterValues,
                                               Map<String, Parameter> existingParameters) {
        long processed = 0, created = 0, updated = 0, errors = 0;

        try {
            // Convert parameter key to human-readable name
            String parameterName = convertTekraParameterKeyToName(parameterKey);

            // Find or create parameter
            Parameter parameter = findOrCreateTekraParameter(category, parameterKey, parameterName, existingParameters);

            if (parameter == null) {
                errors++;
                return new TekraSyncResult(processed, created, updated, errors);
            }

            boolean isNewParameter = parameter.getId() == null;
            parameter = parameterRepository.save(parameter);

            if (isNewParameter) {
                created++;
                existingParameters.put(parameterKey, parameter);
                log.debug("Created parameter: {} ({})", parameterName, parameterKey);
            } else {
                updated++;
                log.debug("Parameter already exists: {} ({})", parameterName, parameterKey);
            }

            // Sync parameter options
            TekraSyncResult optionsResult = syncTekraParameterOptions(parameter, parameterValues);
            created += optionsResult.created;
            updated += optionsResult.updated;
            errors += optionsResult.errors;

            processed = 1; // One parameter processed

        } catch (Exception e) {
            errors++;
            log.error("Error syncing Tekra parameter {}: {}", parameterKey, e.getMessage());
        }

        return new TekraSyncResult(processed, created, updated, errors);
    }

    private Parameter findOrCreateTekraParameter(Category category, String parameterKey, String parameterName,
                                                 Map<String, Parameter> existingParameters) {
        try {
            // Check if parameter already exists by key
            Parameter parameter = existingParameters.get(parameterKey);

            if (parameter == null) {
                // Check by name as fallback
                parameter = parameterRepository.findByCategoryAndNameBg(category, parameterName)
                        .orElse(null);
            }

            if (parameter == null) {
                // Create new parameter
                parameter = new Parameter();
                parameter.setCategory(category);
                parameter.setTekraKey(parameterKey); // Add this field to Parameter entity
                parameter.setNameBg(parameterName);
                parameter.setNameEn(translateParameterName(parameterName)); // You can implement translation
                parameter.setOrder(getParameterOrder(parameterKey));
            }

            return parameter;

        } catch (Exception e) {
            log.error("Error finding/creating Tekra parameter {}: {}", parameterKey, e.getMessage());
            return null;
        }
    }

    private TekraSyncResult syncTekraParameterOptions(Parameter parameter, Set<String> optionValues) {
        long created = 0, updated = 0, errors = 0;

        try {
            Map<String, ParameterOption> existingOptions = parameterOptionRepository
                    .findByParameterIdOrderByOrderAsc(parameter.getId())
                    .stream()
                    .collect(Collectors.toMap(ParameterOption::getNameBg, o -> o));

            int orderCounter = 0;
            for (String optionValue : optionValues) {
                try {
                    ParameterOption option = existingOptions.get(optionValue);

                    if (option == null) {
                        option = new ParameterOption();
                        option.setParameter(parameter);
                        option.setNameBg(optionValue);
                        option.setNameEn(optionValue); // Same value for now
                        option.setOrder(orderCounter++);

                        parameterOptionRepository.save(option);
                        created++;
                        log.debug("Created parameter option: {} = {}", parameter.getNameBg(), optionValue);
                    } else {
                        updated++;
                        log.debug("Parameter option already exists: {} = {}", parameter.getNameBg(), optionValue);
                    }

                } catch (Exception e) {
                    errors++;
                    log.error("Error processing parameter option {}: {}", optionValue, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Error syncing parameter options for {}: {}", parameter.getNameBg(), e.getMessage());
            errors++;
        }

        return new TekraSyncResult(0, created, updated, errors); // processed = 0 since this is sub-operation
    }

    /**
     * Convert Tekra parameter keys to human-readable names
     */
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

    /**
     * Translate parameter names to English (basic implementation)
     */
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

    /**
     * Get parameter order for consistent display
     */
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

// ============ ENHANCED PRODUCT SYNC WITH PARAMETERS ============

    /**
     * Enhanced product sync that includes parameters
     */
    private void updateProductFieldsFromTekraXMLWithParameters(Product product, Map<String, Object> rawData, String categorySlug) {
        // Call the basic update method first
        updateProductFieldsFromTekraXML(product, rawData, categorySlug);

        // Then add parameters
        setTekraParametersToProduct(product, rawData);
    }

    private void setTekraParametersToProduct(Product product, Map<String, Object> rawData) {
        if (product.getCategory() == null) {
            log.warn("Product has no category, cannot set parameters");
            return;
        }

        try {
            Set<ProductParameter> productParameters = new HashSet<>();
            Map<String, Parameter> categoryParameters = cachedLookupService.getParametersByCategory(product.getCategory());

            // Process all prop_* fields as parameters
            for (Map.Entry<String, Object> entry : rawData.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (key.startsWith("prop_") && value != null) {
                    String parameterKey = key.substring(5); // Remove "prop_" prefix
                    String parameterValue = value.toString().trim();

                    if (!parameterValue.isEmpty()) {
                        Parameter parameter = categoryParameters.get(parameterKey);

                        if (parameter != null) {
                            // Find parameter option
                            parameterOptionRepository.findByParameterAndNameBg(parameter, parameterValue)
                                    .ifPresent(option -> {
                                        ProductParameter pp = new ProductParameter();
                                        pp.setProduct(product);
                                        pp.setParameter(parameter);
                                        pp.setParameterOption(option);
                                        productParameters.add(pp);
                                    });
                        }
                    }
                }
            }

            product.setProductParameters(productParameters);

            log.debug("Set {} parameters for product {}", productParameters.size(), product.getSku());

        } catch (Exception e) {
            log.error("Error setting Tekra parameters to product {}: {}", product.getSku(), e.getMessage());
        }
    }

// ============ COMPLETE TEKRA SYNC WITH PARAMETERS ============

    @Transactional
    public TekraSyncResult syncTekraComplete() {
        log.info("Starting complete Tekra synchronization (categories + manufacturers + parameters + products)");
        long totalProcessed = 0, totalCreated = 0, totalUpdated = 0, totalErrors = 0;

        try {
            // Step 1: Sync categories
            log.info("Step 1: Syncing categories...");
            TekraSyncResult categoriesResult = syncTekraCategories();
            totalProcessed += categoriesResult.processed;
            totalCreated += categoriesResult.created;
            totalUpdated += categoriesResult.updated;
            totalErrors += categoriesResult.errors;

            // Step 2: Sync manufacturers
            log.info("Step 2: Syncing manufacturers...");
            TekraSyncResult manufacturersResult = syncTekraManufacturers();
            totalProcessed += manufacturersResult.processed;
            totalCreated += manufacturersResult.created;
            totalUpdated += manufacturersResult.updated;
            totalErrors += manufacturersResult.errors;

            // Step 3: Sync parameters
            log.info("Step 3: Syncing parameters...");
            TekraSyncResult parametersResult = syncTekraParameters();
            totalProcessed += parametersResult.processed;
            totalCreated += parametersResult.created;
            totalUpdated += parametersResult.updated;
            totalErrors += parametersResult.errors;

            // Step 4: Sync products with parameters
            log.info("Step 4: Syncing products with parameters...");
            TekraSyncResult productsResult = syncTekraProductsWithParameters();
            totalProcessed += productsResult.processed;
            totalCreated += productsResult.created;
            totalUpdated += productsResult.updated;
            totalErrors += productsResult.errors;

            log.info("Complete Tekra sync finished - Processed: {}, Created: {}, Updated: {}, Errors: {}",
                    totalProcessed, totalCreated, totalUpdated, totalErrors);

            return new TekraSyncResult(totalProcessed, totalCreated, totalUpdated, totalErrors);

        } catch (Exception e) {
            log.error("Error during complete Tekra synchronization", e);
            throw e;
        }
    }

    private TekraSyncResult syncTekraProductsWithParameters() {
        long totalProcessed = 0, totalCreated = 0, totalUpdated = 0, totalErrors = 0;

        try {
            String testCategory = "videonablyudenie";
            List<Map<String, Object>> rawProducts = tekraApiService.getProductsRaw(testCategory);

            for (Map<String, Object> rawProduct : rawProducts) {
                try {
                    String sku = getStringValue(rawProduct, "sku");
                    if (sku == null) continue;

                    Optional<Product> existingProduct = productRepository.findBySku(sku);

                    if (existingProduct.isPresent()) {
                        updateProductFieldsFromTekraXMLWithParameters(existingProduct.get(), rawProduct, testCategory);
                        productRepository.save(existingProduct.get());
                        totalUpdated++;
                    } else {
                        Product newProduct = new Product();
                        updateProductFieldsFromTekraXMLWithParameters(newProduct, rawProduct, testCategory);
                        productRepository.save(newProduct);
                        totalCreated++;
                    }

                    totalProcessed++;

                } catch (Exception e) {
                    totalErrors++;
                    log.error("Error processing product with parameters: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Error syncing products with parameters", e);
            totalErrors++;
        }

        return new TekraSyncResult(totalProcessed, totalCreated, totalUpdated, totalErrors);
    }

    // ============ SINGLE CATEGORY SYNC (For testing) ============
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

    // ============ UTILITY METHODS ============
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

    private void syncParameterOptionsChunked(Parameter parameter, List<ParameterOptionRequestDto> allOptions) {
        if (allOptions == null || allOptions.isEmpty()) {
            return;
        }

        Map<Long, ParameterOption> existingOptions = parameterOptionRepository
                .findByParameterIdOrderByOrderAsc(parameter.getId())
                .stream()
                .collect(Collectors.toMap(ParameterOption::getExternalId, o -> o));

        List<List<ParameterOptionRequestDto>> chunks = partitionList(allOptions, 20);

        for (List<ParameterOptionRequestDto> chunk : chunks) {
            for (ParameterOptionRequestDto extOption : chunk) {
                try {
                    ParameterOption option = existingOptions.get(extOption.getId());

                    if (option == null) {
                        option = createParameterOptionFromExternal(extOption, parameter);
                        parameterOptionRepository.save(option);
                    } else {
                        updateParameterOptionFromExternal(option, extOption);
                        parameterOptionRepository.save(option);
                    }
                } catch (Exception e) {
                    log.error("Error processing parameter option {}: {}", extOption.getId(), e.getMessage());
                }
            }
        }
    }

//    private void monitorConnectionPool() {
//        try {
//            if (dataSource instanceof HikariDataSource) {
//                HikariDataSource ds = (HikariDataSource) dataSource;
//                int active = ds.getHikariPoolMXBean().getActiveConnections();
//                int total = ds.getHikariPoolMXBean().getTotalConnections();
//                int idle = ds.getHikariPoolMXBean().getIdleConnections();
//
//                log.debug("Connection Pool Status - Active: {}, Idle: {}, Total: {}", active, idle, total);
//
//                if (active > (total * 0.8)) {
//                    log.warn("Connection pool usage high: {}/{} ({}%)", active, total, (active * 100 / total));
//                }
//            }
//        } catch (Exception e) {
//            log.debug("Could not retrieve connection pool stats: {}", e.getMessage());
//        }
//    }

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

    // ============ RESULT CLASSES ============
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

    private static class TekraSyncResult {
        long processed, created, updated, errors;

        TekraSyncResult(long processed, long created, long updated, long errors) {
            this.processed = processed;
            this.created = created;
            this.updated = updated;
            this.errors = errors;
        }
    }

    // ============ ENTITY CREATION/UPDATE METHODS ============
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

    private void setParametersToProduct(Product product, ProductRequestDto extProduct) {
        if (extProduct.getParameters() != null && product.getCategory() != null) {

            Set<ProductParameter> newProductParameters = extProduct.getParameters().stream()
                    .map(paramValue -> cachedLookupService
                            .getParameter(paramValue.getParameterId(), product.getCategory().getId())
                            .flatMap(parameter -> cachedLookupService
                                    .getParameterOption(paramValue.getOptionId(), parameter.getId())
                                    .map(option -> {
                                        ProductParameter pp = new ProductParameter();
                                        pp.setProduct(product);
                                        pp.setParameter(parameter);
                                        pp.setParameterOption(option);
                                        return pp;
                                    })
                            )
                    )
                    .flatMap(Optional::stream)
                    .collect(Collectors.toSet());

            product.setProductParameters(newProductParameters);

        } else {
            product.setProductParameters(new HashSet<>());
        }
    }


    private String generateSlug(String name) {
        return name == null ? null :
                name.toLowerCase()
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("^-|-$", "");
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
        category.setSlug(generateSlug(baseName));

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
            String baseName = category.getNameEn() != null ? category.getNameEn() : category.getNameBg();
            category.setSlug(generateSlug(baseName));
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

    private ParameterOption createParameterOptionFromExternal(ParameterOptionRequestDto extOption, Parameter parameter) {
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

    private void updateParameterOptionFromExternal(ParameterOption option, ParameterOptionRequestDto extOption) {
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
}