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
import com.techstore.repository.CategoryRepository;
import com.techstore.repository.ManufacturerRepository;
import com.techstore.repository.ParameterOptionRepository;
import com.techstore.repository.ParameterRepository;
import com.techstore.repository.ProductRepository;
import com.techstore.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            syncManufacturers();
            syncParameters();
            syncProducts();
            log.info("Scheduled synchronization completed successfully");
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

                Map<Long, Parameter> existingParameters = parameterRepository.findByCategoryIdOrderByOrderAsc(category.getId())
                        .stream()
                        .collect(Collectors.toMap(Parameter::getExternalId, p -> p));

                for (ExternalParameterDto extParameter : externalParameters) {
                    Parameter parameter = existingParameters.get(extParameter.getId());

                    if (parameter == null) {
                        parameter = createParameterFromExternal(extParameter, category);
                        created++;
                    } else {
                        updateParameterFromExternal(parameter, extParameter);
                        updated++;
                    }

                    parameterRepository.save(parameter);
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

    // Helper methods
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

    private void syncParameterOptions(Parameter parameter, List<ExternalParameterOptionDto> externalOptions) {
        Map<Long, ParameterOption> existingOptions = parameterOptionRepository.findByParameterIdOrderByOrderAsc(parameter.getExternalId())
                .stream()
                .collect(Collectors.toMap(ParameterOption::getExternalId, o -> o));

        for (ExternalParameterOptionDto extOption : externalOptions) {
            ParameterOption option = existingOptions.get(extOption.getId());

            if (option == null) {
                option = createParameterOptionFromExternal(extOption, parameter);
            } else {
                updateParameterOptionFromExternal(option, extOption);
            }

            parameterOptionRepository.save(option);
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
    public void syncProducts() {
        SyncLog syncLog = createSyncLog("PRODUCTS");
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting products synchronization");

            long totalProcessed = 0, created = 0, updated = 0;
            int currentPage = 1;
            PaginatedProductsDto productPage;

            Map<Long, Manufacturer> manufacturersMap = manufacturerRepository.findAll()
                    .stream()
                    .collect(Collectors.toMap(Manufacturer::getExternalId, m -> m));

            do {
                productPage = valiApiService.getProducts(currentPage, batchSize);

                if (productPage.getItems() != null) {
                    for (ExternalProductDto extProduct : productPage.getItems()) {
                        try {
                            Optional<Product> existingProduct = productRepository.findByExternalId(extProduct.getId());

                            if (existingProduct.isPresent()) {
                                updateProductFromExternal(existingProduct.get(), extProduct, manufacturersMap);
                                updated++;
                            } else {
                                createProductFromExternal(extProduct, manufacturersMap);
                                created++;
                            }

                            totalProcessed++;
                        } catch (Exception e) {
                            log.error("Error processing product {}: {}", extProduct.getId(), e.getMessage());
                        }
                    }
                }

                currentPage++;
                log.info("Processed page {} of {} products", currentPage - 1, productPage.getTotalItems());

            } while (currentPage <= productPage.getLastPage());

            syncLog.setStatus("SUCCESS");
            syncLog.setRecordsProcessed(totalProcessed);
            syncLog.setRecordsCreated(created);
            syncLog.setRecordsUpdated(updated);

            log.info("Products synchronization completed - Created: {}, Updated: {}", created, updated);

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

    private void createProductFromExternal(ExternalProductDto extProduct, Map<Long, Manufacturer> manufacturersMap) {
        Manufacturer manufacturer = manufacturersMap.get(extProduct.getManufacturerId());
        if (manufacturer == null) {
            log.warn("Manufacturer not found for product {}: {}", extProduct.getId(), extProduct.getManufacturerId());
            return;
        }

        Product product = new Product();
        updateProductFieldsFromExternal(product, extProduct, manufacturer);

        product = productRepository.save(product);

        // Create related entities
        createProductRelatedEntities(product, extProduct);
    }

    private void updateProductFromExternal(Product product, ExternalProductDto extProduct, Map<Long, Manufacturer> manufacturersMap) {
        Manufacturer manufacturer = manufacturersMap.get(extProduct.getManufacturerId());
        if (manufacturer == null) {
            log.warn("Manufacturer not found for product {}: {}", extProduct.getId(), extProduct.getManufacturerId());
            return;
        }

        updateProductFieldsFromExternal(product, extProduct, manufacturer);

        product = productRepository.save(product);

        // Update related entities
        updateProductRelatedEntities(product, extProduct);
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
}