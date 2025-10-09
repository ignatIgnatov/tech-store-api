package com.techstore.config;

import com.techstore.entity.Category;
import com.techstore.entity.Parameter;
import com.techstore.entity.ParameterOption;
import com.techstore.entity.Product;
import com.techstore.repository.CategoryRepository;
import com.techstore.repository.ParameterOptionRepository;
import com.techstore.repository.ParameterRepository;
import com.techstore.repository.ProductRepository;
import com.techstore.service.sync.ValiSyncService;
import com.techstore.service.TekraApiService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Hidden
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/debug")
public class DebugController {

    private final ValiSyncService valiSyncService;
    private final TekraApiService tekraApiService;
    private final CategoryRepository categoryRepository;
    private final ParameterRepository parameterRepository;
    private final ParameterOptionRepository parameterOptionRepository;
    private final ProductRepository productRepository;

    @GetMapping("/admin/debug-hdd-parameters")
    public String debugHddParameters() {
        log.info("=== DEBUGGING HDD PARAMETERS ===");

        // 1. Check category
        Optional<Category> hddCategoryOpt = categoryRepository.findById(5L);
        if (hddCategoryOpt.isEmpty()) {
            return "HDD category not found!";
        }

        Category hddCategory = hddCategoryOpt.get();
        log.info("HDD Category: ID={}, name='{}', path='{}'",
                hddCategory.getId(), hddCategory.getNameBg(), hddCategory.getCategoryPath());

        // 2. Check parameters for this category
        List<Parameter> parameters = parameterRepository.findByCategoryId(hddCategory.getId());
        log.info("Found {} parameters for HDD category", parameters.size());

        if (!parameters.isEmpty()) {
            log.info("Parameters:");
            parameters.forEach(p ->
                    log.info("  - {} (tekraKey: '{}', options: {})",
                            p.getNameBg(), p.getTekraKey(),
                            parameterOptionRepository.findByParameterIdOrderByOrderAsc(p.getId()).size())
            );
        }

        // 3. Check actual HDD products
        List<Product> hddProducts = productRepository.findAll().stream()
                .filter(p -> p.getCategory() != null && p.getCategory().getId().equals(5L))
                .limit(3)
                .toList();

        log.info("\nFound {} products in HDD category", hddProducts.size());

        // 4. Check XML for sample HDD product
        if (!hddProducts.isEmpty()) {
            Product sampleProduct = hddProducts.get(0);
            log.info("\n=== Sample HDD Product ===");
            log.info("SKU: {}", sampleProduct.getSku());
            log.info("Name: {}", sampleProduct.getNameBg());
            log.info("Current parameters: {}", sampleProduct.getProductParameters().size());

            // Fetch from Tekra API
            List<Map<String, Object>> tekraProducts = tekraApiService.getProductsRaw("videonablyudenie");
            Optional<Map<String, Object>> xmlProduct = tekraProducts.stream()
                    .filter(p -> sampleProduct.getSku().equals(getString(p, "sku")))
                    .findFirst();

            if (xmlProduct.isPresent()) {
                Map<String, Object> rawProduct = xmlProduct.get();

                log.info("\n=== XML Data for product {} ===", sampleProduct.getSku());
                log.info("category_1: '{}'", getString(rawProduct, "category_1"));
                log.info("category_2: '{}'", getString(rawProduct, "category_2"));
                log.info("category_3: '{}'", getString(rawProduct, "category_3"));

                // Extract parameters
                Map<String, String> params = extractTekraParameters(rawProduct);
                log.info("\nExtracted {} parameters from XML:", params.size());
                params.forEach((key, value) ->
                        log.info("  - {}: '{}'", key, value)
                );

                // Try to find matching parameters in DB
                log.info("\n=== Parameter Matching ===");
                for (Map.Entry<String, String> param : params.entrySet()) {
                    String paramKey = param.getKey();
                    String paramValue = param.getValue();

                    Optional<Parameter> paramOpt = parameterRepository
                            .findByTekraKeyAndCategoryId(paramKey, hddCategory.getId());

                    if (paramOpt.isPresent()) {
                        log.info("✓ Found parameter '{}' in DB", paramKey);

                        Parameter parameter = paramOpt.get();
                        Optional<ParameterOption> optionOpt = parameterOptionRepository
                                .findByParameterAndNameBg(parameter, paramValue);

                        if (optionOpt.isPresent()) {
                            log.info("  ✓ Found option '{}' for parameter '{}'", paramValue, paramKey);
                        } else {
                            log.warn("  ✗ Option '{}' NOT FOUND for parameter '{}'", paramValue, paramKey);
                        }
                    } else {
                        log.warn("✗ Parameter '{}' NOT FOUND in DB for category {}",
                                paramKey, hddCategory.getId());
                    }
                }
            } else {
                log.warn("Product {} not found in Tekra XML", sampleProduct.getSku());
            }
        }

        return "Check logs for details";
    }


    @GetMapping("/admin/find-hdd-products")
    public String findHddProducts() {
        log.info("=== SEARCHING FOR HDD PRODUCTS IN ALL CATEGORIES ===");

        List<Category> allTekraCategories = categoryRepository.findAll().stream()
                .filter(cat -> cat.getTekraSlug() != null)
                .toList();

        int totalHddProducts = 0;
        Map<String, Integer> hddProductsByCategory = new HashMap<>();

        for (Category category : allTekraCategories) {
            try {
                List<Map<String, Object>> products = tekraApiService.getProductsRaw(category.getTekraSlug());

                for (Map<String, Object> product : products) {
                    String cat1 = getString(product, "category_1");
                    String cat2 = getString(product, "category_2");
                    String cat3 = getString(product, "category_3");
                    String name = getString(product, "name");

                    // Check if this is an HDD product
                    boolean isHdd = false;
                    String hddLocation = "";

                    if ("hdd".equalsIgnoreCase(cat2) || "твърди дискове".equalsIgnoreCase(cat2)) {
                        isHdd = true;
                        hddLocation = "category_2";
                    } else if ("hdd".equalsIgnoreCase(cat3) || "твърди дискове".equalsIgnoreCase(cat3)) {
                        isHdd = true;
                        hddLocation = "category_3";
                    } else if (name != null && (name.toLowerCase().contains("hdd") ||
                            name.toLowerCase().contains("твърд диск"))) {
                        isHdd = true;
                        hddLocation = "name";
                    }

                    if (isHdd) {
                        totalHddProducts++;
                        String key = category.getTekraSlug() + " (" + hddLocation + ")";
                        hddProductsByCategory.put(key, hddProductsByCategory.getOrDefault(key, 0) + 1);

                        if (totalHddProducts <= 3) {
                            log.info("\n=== HDD Product #{} ===", totalHddProducts);
                            log.info("  Name: {}", name);
                            log.info("  Found in category: {}", category.getTekraSlug());
                            log.info("  category_1: '{}'", cat1);
                            log.info("  category_2: '{}'", cat2);
                            log.info("  category_3: '{}'", cat3);
                            log.info("  Identified by: {}", hddLocation);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error processing category {}: {}", category.getTekraSlug(), e.getMessage());
            }
        }

        log.info("\n=== SUMMARY ===");
        log.info("Total HDD products found: {}", totalHddProducts);
        log.info("\nBreakdown by source category:");
        hddProductsByCategory.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .forEach(entry -> log.info("  {}: {} products", entry.getKey(), entry.getValue()));

        return String.format("Found %d HDD products. Check logs for details.", totalHddProducts);
    }

    @GetMapping("/admin/debug-hdd-products")
    public String debugHddProducts() {
        log.info("=== DEBUGGING HDD PRODUCTS ===");

        // Fetch products from "hdd" category
        List<Map<String, Object>> products = tekraApiService.getProductsRaw("hdd");

        log.info("Found {} products in 'hdd' category", products.size());

        // Show first 3 products
        products.stream().limit(3).forEach(p -> {
            String sku = getString(p, "sku");
            String name = getString(p, "name");
            String cat1 = getString(p, "category_1");
            String cat2 = getString(p, "category_2");
            String cat3 = getString(p, "category_3");

            log.info("\n=== Product: {} ===", sku);
            log.info("  Name: {}", name);
            log.info("  category_1: '{}'", cat1);
            log.info("  category_2: '{}'", cat2);
            log.info("  category_3: '{}'", cat3);

            String expectedPath = buildCategoryPath(cat1, cat2, cat3);
            log.info("  Expected path: '{}'", expectedPath);

            // Check if category exists
            Optional<Category> match = categoryRepository.findAll().stream()
                    .filter(c -> c.getCategoryPath() != null)
                    .filter(c -> expectedPath.equalsIgnoreCase(c.getCategoryPath()))
                    .findFirst();

            if (match.isPresent()) {
                log.info("  ✓ MATCH FOUND: ID={}, name='{}'",
                        match.get().getId(), match.get().getNameBg());
            } else {
                log.warn("  ✗ NO MATCH for path: '{}'", expectedPath);

                // Try partial match (L1+L2)
                if (cat2 != null) {
                    String partialPath = buildCategoryPath(cat1, cat2, null);
                    Optional<Category> partialMatch = categoryRepository.findAll().stream()
                            .filter(c -> c.getCategoryPath() != null)
                            .filter(c -> partialPath.equalsIgnoreCase(c.getCategoryPath()))
                            .findFirst();

                    if (partialMatch.isPresent()) {
                        log.info("  ✓ PARTIAL MATCH (L1+L2): ID={}, name='{}'",
                                partialMatch.get().getId(), partialMatch.get().getNameBg());
                    } else {
                        log.warn("  ✗ NO PARTIAL MATCH for: '{}'", partialPath);
                    }
                }
            }
        });

        return "Check logs";
    }

    @GetMapping("/admin/analyze-product-categories")
    public String analyzeProductCategories() {
        List<Map<String, Object>> allProducts = new ArrayList<>();

        // Fetch products under "videonablyudenie"
        List<Map<String, Object>> products = tekraApiService.getProductsRaw("videonablyudenie");
        allProducts.addAll(products);

        // Group by category_1
        Map<String, Long> category1Counts = allProducts.stream()
                .collect(Collectors.groupingBy(
                        p -> {
                            String cat1 = getString(p, "category_1");
                            return cat1 != null ? cat1 : "NULL";
                        },
                        Collectors.counting()
                ));

        log.info("=== PRODUCTS UNDER videonablyudenie ===");
        log.info("Total products: {}", allProducts.size());
        log.info("Breakdown by category_1:");
        category1Counts.forEach((cat, count) ->
                log.info("  - '{}': {} products", cat, count)
        );

        return "Check logs";
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
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
                String value = getString(rawProduct, paramKey);
                if (value != null && !value.trim().isEmpty()) {
                    parameters.put(paramKey, value.trim());
                }
            }
        }

        return parameters;
    }
}
