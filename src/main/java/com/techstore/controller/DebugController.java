package com.techstore.controller;

import com.techstore.entity.Parameter;
import com.techstore.entity.ParameterOption;
import com.techstore.entity.Product;
import com.techstore.repository.ParameterOptionRepository;
import com.techstore.repository.ParameterRepository;
import com.techstore.repository.ProductRepository;
import com.techstore.service.ProductService;
import com.techstore.service.SyncService;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
public class DebugController {

    private final SyncService syncService;
    private final ProductRepository productRepository;
    private final ParameterRepository parameterRepository;
    private final ParameterOptionRepository parameterOptionRepository;
    private final ProductService productService;

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
