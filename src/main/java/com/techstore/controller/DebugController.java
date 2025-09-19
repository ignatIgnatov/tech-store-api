package com.techstore.controller;

import com.techstore.dto.request.ProductRequestDto;
import com.techstore.entity.Parameter;
import com.techstore.entity.ParameterOption;
import com.techstore.entity.Product;
import com.techstore.repository.ParameterOptionRepository;
import com.techstore.repository.ParameterRepository;
import com.techstore.repository.ProductRepository;
import com.techstore.service.ProductService;
import com.techstore.service.SyncService;
import com.techstore.service.ValiApiService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Hidden
@Slf4j
@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
public class DebugController {

    private final SyncService syncService;
    private final ProductRepository productRepository;
    private final ParameterRepository parameterRepository;
    private final ParameterOptionRepository parameterOptionRepository;
    private final ProductService productService;
    private final ValiApiService valiApiService;

    @PostMapping("/sync-single-product/{externalId}")
    public ResponseEntity<Map<String, Object>> debugSyncSingleProduct(@PathVariable Long externalId) {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("=== DEBUG SINGLE PRODUCT SYNC ===");
            log.info("External Product ID: {}", externalId);

            // Намери продукта в базата
            Optional<Product> productOpt = productRepository.findByExternalId(externalId);
            if (productOpt.isEmpty()) {
                result.put("error", "Product not found with external ID: " + externalId);
                return ResponseEntity.notFound().build();
            }

            Product product = productOpt.get();
            log.info("Found product: ID={}, Reference={}, Category ID={}",
                    product.getId(), product.getReferenceNumber(), product.getCategory().getId());

            // Получи данните от Vali API
            log.info("Fetching product data from Vali API...");

            // За тестване, ще имитирам извикването към ValiApiService
            // В реалната имплементация трябва да извикваш valiApiService.getProductById(externalId)
            // Или да добавиш такъв метод в ValiApiService

            // За сега ще покажем какви параметри има продукта преди sync
            log.info("Current product parameters count: {}", product.getProductParameters().size());
            product.getProductParameters().forEach(pp -> {
                log.info("Current PP: Parameter '{}' (ID: {}, External: {}) -> Option '{}' (ID: {}, External: {})",
                        pp.getParameter().getNameEn(),
                        pp.getParameter().getId(),
                        pp.getParameter().getExternalId(),
                        pp.getParameterOption().getNameEn(),
                        pp.getParameterOption().getId(),
                        pp.getParameterOption().getExternalId());
            });

            // Направи sync за цялата категория (за да се актуализира продукта)
            log.info("Starting sync for category: {} (internal ID: {})",
                    product.getCategory().getExternalId(), product.getCategory().getId());
            syncService.syncProductsByCategory(product.getCategory().getId());

            // Обнови продукта от базата данни
            productRepository.flush();
            Product updatedProduct = productRepository.findById(product.getId()).get();

            log.info("After sync - product parameters count: {}", updatedProduct.getProductParameters().size());
            updatedProduct.getProductParameters().forEach(pp -> {
                log.info("After sync PP: Parameter '{}' (ID: {}, External: {}) -> Option '{}' (ID: {}, External: {})",
                        pp.getParameter().getNameEn(),
                        pp.getParameter().getId(),
                        pp.getParameter().getExternalId(),
                        pp.getParameterOption().getNameEn(),
                        pp.getParameterOption().getId(),
                        pp.getParameterOption().getExternalId());
            });

            // Попълни резултата
            result.put("success", true);
            result.put("productId", product.getId());
            result.put("externalId", externalId);
            result.put("referenceNumber", product.getReferenceNumber());
            result.put("categoryId", product.getCategory().getId());
            result.put("categoryExternalId", product.getCategory().getExternalId());
            result.put("parametersCountBefore", product.getProductParameters().size());
            result.put("parametersCountAfter", updatedProduct.getProductParameters().size());

            result.put("parametersAfter", updatedProduct.getProductParameters().stream()
                    .map(pp -> Map.of(
                            "parameterName", pp.getParameter().getNameEn(),
                            "parameterExternalId", pp.getParameter().getExternalId(),
                            "optionName", pp.getParameterOption().getNameEn(),
                            "optionExternalId", pp.getParameterOption().getExternalId()
                    ))
                    .collect(Collectors.toList()));

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error during single product sync debug", e);
            result.put("error", e.getMessage());
            result.put("success", false);
            return ResponseEntity.status(500).body(result);
        }
    }

    @GetMapping("/vali-api-product/{externalId}")
    public ResponseEntity<Map<String, Object>> debugValiApiProduct(@PathVariable Long externalId) {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("=== DEBUG VALI API PRODUCT DATA ===");
            log.info("External Product ID: {}", externalId);

            // Намери продукта в базата за да получим category
            Optional<Product> productOpt = productRepository.findByExternalId(externalId);
            if (productOpt.isEmpty()) {
                result.put("error", "Product not found with external ID: " + externalId);
                return ResponseEntity.notFound().build();
            }

            Product product = productOpt.get();
            Long categoryExternalId = product.getCategory().getExternalId();

            // Получи всички продукти за тази категория от Vali API
            log.info("Fetching products for category: {}", categoryExternalId);
            List<ProductRequestDto> allProducts =
                    valiApiService.getProductsByCategory(categoryExternalId);

            // Намери нашия продукт
            Optional<ProductRequestDto> ourProduct = allProducts.stream()
                    .filter(p -> p.getId().equals(externalId))
                    .findFirst();

            if (ourProduct.isEmpty()) {
                result.put("error", "Product not found in Vali API response");
                result.put("totalProductsInCategory", allProducts.size());
                return ResponseEntity.notFound().build();
            }

            ProductRequestDto valiProduct = ourProduct.get();

            log.info("Found product in Vali API: {}", valiProduct.getReferenceNumber());
            log.info("Parameters from Vali API: {}",
                    valiProduct.getParameters() != null ? valiProduct.getParameters().size() : 0);

            result.put("success", true);
            result.put("productId", product.getId());
            result.put("externalId", externalId);
            result.put("referenceNumber", valiProduct.getReferenceNumber());
            result.put("valiParametersCount", valiProduct.getParameters() != null ? valiProduct.getParameters().size() : 0);

            if (valiProduct.getParameters() != null) {
                List<Map<String, Object>> parameterDetails = valiProduct.getParameters().stream()
                        .map(param -> {
                            Map<String, Object> paramMap = new HashMap<>();
                            paramMap.put("parameterExternalId", param.getParameterId());
                            paramMap.put("optionExternalId", param.getOptionId());

                            if (param.getParameterName() != null) {
                                param.getParameterName().forEach(name -> {
                                    paramMap.put("parameterName_" + name.getLanguageCode(), name.getText());
                                });
                            }

                            if (param.getOptionName() != null) {
                                param.getOptionName().forEach(name -> {
                                    paramMap.put("optionName_" + name.getLanguageCode(), name.getText());
                                });
                            }

                            return paramMap;
                        })
                        .collect(Collectors.toList());

                result.put("valiParameters", parameterDetails);
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error fetching Vali API product data", e);
            result.put("error", e.getMessage());
            result.put("success", false);
            return ResponseEntity.status(500).body(result);
        }
    }

    @PostMapping("/sync-product/{externalId}")
    public ResponseEntity<Map<String, Object>> debugSyncProduct(@PathVariable Long externalId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Намери продукта в базата
            Optional<Product> productOpt = productRepository.findByExternalId(externalId);
            if (productOpt.isEmpty()) {
                result.put("error", "Product not found with external ID: " + externalId);
                return ResponseEntity.notFound().build();
            }

            Product product = productOpt.get();
            result.put("productId", product.getId());
            result.put("externalId", externalId);
            result.put("categoryId", product.getCategory().getId());
            result.put("categoryExternalId", product.getCategory().getExternalId());

            // Синхронизирай само тази категория
            syncService.syncProductsByCategory(product.getCategory().getExternalId());

            // Провери резултата
            productRepository.flush();
            Product updatedProduct = productRepository.findById(product.getId()).get();

            result.put("parametersCount", updatedProduct.getProductParameters().size());
            result.put("parameters", updatedProduct.getProductParameters().stream()
                    .map(pp -> Map.of(
                            "parameterName", pp.getParameter().getNameEn(),
                            "parameterExternalId", pp.getParameter().getExternalId(),
                            "optionName", pp.getParameterOption().getNameEn(),
                            "optionExternalId", pp.getParameterOption().getExternalId()
                    ))
                    .collect(Collectors.toList()));

            result.put("success", true);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("success", false);
            return ResponseEntity.status(500).body(result);
        }
    }

    @GetMapping("/check-parameters/{categoryId}")
    public ResponseEntity<Map<String, Object>> checkCategoryParameters(@PathVariable Long categoryId) {
        Map<String, Object> result = new HashMap<>();

        // Провери има ли параметри за тази категория
        List<Parameter> parameters = parameterRepository.findByCategoryIdOrderByOrderAsc(categoryId);

        result.put("categoryId", categoryId);
        result.put("parametersCount", parameters.size());
        result.put("parameters", parameters.stream()
                .map(p -> Map.of(
                        "id", p.getId(),
                        "externalId", p.getExternalId(),
                        "nameEn", p.getNameEn(),
                        "nameBg", p.getNameBg(),
                        "optionsCount", p.getOptions().size()
                ))
                .collect(Collectors.toList()));

        return ResponseEntity.ok(result);
    }

    @GetMapping("/product-parameters/{productId}")
    public ResponseEntity<String> debugProductParameters(@PathVariable Long productId) {
        try {
            productService.debugProductParameters(productId);
            return ResponseEntity.ok("Debug completed - check logs");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/check-parameter-options/{externalParamId}")
    public ResponseEntity<Map<String, Object>> checkParameterOptions(@PathVariable Long externalParamId) {
        Map<String, Object> result = new HashMap<>();

        // Намери параметъра по external ID
        Optional<Parameter> paramOpt = parameterRepository.findByExternalId(externalParamId);

        if (paramOpt.isEmpty()) {
            result.put("error", "Parameter not found with external ID: " + externalParamId);
            return ResponseEntity.notFound().build();
        }

        Parameter param = paramOpt.get();
        List<ParameterOption> options = parameterOptionRepository.findByParameterIdOrderByOrderAsc(param.getId());

        result.put("parameterId", param.getId());
        result.put("parameterExternalId", externalParamId);
        result.put("parameterName", param.getNameEn());
        result.put("optionsCount", options.size());
        result.put("options", options.stream()
                .map(o -> Map.of(
                        "id", o.getId(),
                        "externalId", o.getExternalId(),
                        "nameEn", o.getNameEn(),
                        "nameBg", o.getNameBg()
                ))
                .collect(Collectors.toList()));

        return ResponseEntity.ok(result);
    }
}
