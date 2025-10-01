package com.techstore.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TekraApiService {

    private final RestTemplate restTemplate;

    @Value("${tekra.api.base-url:https://tekra.bg/shop/api}")
    private String baseUrl;

    @Value("${tekra.api.access-token}")
    private String accessToken;

    @Value("${tekra.api.enabled:false}")
    private boolean tekraApiEnabled;

    private final Map<String, List<Map<String, Object>>> productsCache = new HashMap<>();
    private long cacheTimestamp = 0;
    private static final long CACHE_DURATION_MS = 5 * 60 * 1000;

    /**
     * Get categories using JSON parsing (categories return JSON)
     */
    public List<Map<String, Object>> getCategoriesRaw() {
        if (!tekraApiEnabled) {
            log.warn("Tekra API is disabled");
            return new ArrayList<>();
        }

        try {
            log.info("Fetching categories from Tekra API");

            String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .queryParam("action", "categories")
                    .queryParam("access_token_feed", accessToken)
                    .toUriString();

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response == null) {
                log.error("Received null response from Tekra API");
                return new ArrayList<>();
            }

            List<Map<String, Object>> categories = extractTekraCategoriesFromResponse(response);
            log.info("Extracted {} categories from Tekra response", categories.size());
            return categories;

        } catch (Exception e) {
            log.error("Error fetching categories from Tekra API", e);
            return new ArrayList<>();
        }
    }

    /**
     * Test API connectivity
     */
    public boolean testConnectionRaw() {
        if (!tekraApiEnabled) {
            return false;
        }

        try {
            List<Map<String, Object>> categories = getCategoriesRaw();
            boolean isConnected = !categories.isEmpty();
            log.info("Tekra API connection test: {}", isConnected ? "SUCCESS" : "FAILED");
            return isConnected;
        } catch (Exception e) {
            log.error("Tekra API connection test failed", e);
            return false;
        }
    }

    // JSON parsing for categories (same as before)
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractTekraCategoriesFromResponse(Map<String, Object> response) {
        List<Map<String, Object>> categories = new ArrayList<>();

        try {
            Object dataObj = response.get("data");
            if (dataObj instanceof Map) {
                Map<String, Object> dataMap = (Map<String, Object>) dataObj;
                Object categoriesObj = dataMap.get("categories");
                if (categoriesObj instanceof List) {
                    List<?> categoriesList = (List<?>) categoriesObj;
                    for (Object item : categoriesList) {
                        if (item instanceof Map) {
                            categories.add((Map<String, Object>) item);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error extracting categories from Tekra response", e);
        }

        return categories;
    }

    /**
     * Extract all unique parameters from Tekra products XML
     */
    public Map<String, Set<String>> extractTekraParametersFromProducts(String categorySlug) {
        Map<String, Set<String>> parametersMap = new HashMap<>();

        try {
            List<Map<String, Object>> products = getProductsRaw(categorySlug);

            for (Map<String, Object> product : products) {
                // Extract all prop_* fields as parameters
                for (Map.Entry<String, Object> entry : product.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();

                    if (key.startsWith("prop_") && value != null) {
                        String parameterName = key.substring(5); // Remove "prop_" prefix
                        String parameterValue = value.toString().trim();

                        if (!parameterValue.isEmpty()) {
                            parametersMap.computeIfAbsent(parameterName, k -> new HashSet<>()).add(parameterValue);
                        }
                    }
                }
            }

            log.info("Extracted {} unique parameters from Tekra products", parametersMap.size());

        } catch (Exception e) {
            log.error("Error extracting parameters from Tekra products", e);
        }

        return parametersMap;
    }

    /**
     * Extract all unique manufacturers from Tekra products
     */
    public Set<String> extractTekraManufacturersFromProducts(String categorySlug) {
        Set<String> manufacturers = new HashSet<>();

        try {
            List<Map<String, Object>> products = getProductsRaw(categorySlug);

            for (Map<String, Object> product : products) {
                String manufacturer = getStringValue(product, "manufacturer");
                if (manufacturer != null && !manufacturer.isEmpty()) {
                    manufacturers.add(manufacturer);
                }
            }

            log.info("Extracted {} unique manufacturers from Tekra products: {}",
                    manufacturers.size(), manufacturers);

        } catch (Exception e) {
            log.error("Error extracting manufacturers from Tekra products", e);
        }

        return manufacturers;
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString().trim() : null;
    }

    // NEW: XML parsing for products
    private List<Map<String, Object>> parseProductsFromXML(String xmlResponse) {
        List<Map<String, Object>> products = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(xmlResponse.getBytes("UTF-8")));

            NodeList itemNodes = document.getElementsByTagName("item");
            log.info("Found {} product items in XML", itemNodes.getLength());

            for (int i = 0; i < itemNodes.getLength(); i++) {
                Node itemNode = itemNodes.item(i);
                if (itemNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element itemElement = (Element) itemNode;
                    Map<String, Object> product = extractProductFromXMLElement(itemElement);
                    if (product != null) {
                        products.add(product);

                        // Log first product for debugging
                        if (i == 0) {
                            log.info("First product fields: {}", product.keySet());
                            log.info("First product sample: name={}, sku={}, price={}",
                                    product.get("name"), product.get("sku"), product.get("price"));
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error parsing products XML", e);
        }

        return products;
    }

    private Map<String, Object> extractProductFromXMLElement(Element itemElement) {
        Map<String, Object> product = new HashMap<>();

        try {
            // Extract all child elements
            NodeList childNodes = itemElement.getChildNodes();

            for (int i = 0; i < childNodes.getLength(); i++) {
                Node childNode = childNodes.item(i);
                if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element childElement = (Element) childNode;
                    String tagName = childElement.getTagName();
                    String textContent = childElement.getTextContent();

                    // Handle special cases
                    if ("gallery".equals(tagName)) {
                        product.put(tagName, extractGalleryImages(childElement));
                    } else if ("files".equals(tagName)) {
                        product.put(tagName, extractFiles(childElement));
                    } else if (textContent != null && !textContent.trim().isEmpty()) {
                        product.put(tagName, textContent.trim());
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error extracting product from XML element", e);
            return null;
        }

        return product;
    }

    private List<String> extractGalleryImages(Element galleryElement) {
        List<String> images = new ArrayList<>();
        NodeList imageNodes = galleryElement.getElementsByTagName("image");

        for (int i = 0; i < imageNodes.getLength(); i++) {
            String imageUrl = imageNodes.item(i).getTextContent();
            if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                images.add(imageUrl.trim());
            }
        }

        return images;
    }

    private List<Map<String, String>> extractFiles(Element filesElement) {
        List<Map<String, String>> files = new ArrayList<>();
        NodeList fileNodes = filesElement.getElementsByTagName("file");

        for (int i = 0; i < fileNodes.getLength(); i++) {
            if (fileNodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Element fileElement = (Element) fileNodes.item(i);
                Map<String, String> file = new HashMap<>();

                NodeList fileChildNodes = fileElement.getChildNodes();
                for (int j = 0; j < fileChildNodes.getLength(); j++) {
                    Node fileChildNode = fileChildNodes.item(j);
                    if (fileChildNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element fileChildElement = (Element) fileChildNode;
                        file.put(fileChildElement.getTagName(), fileChildElement.getTextContent());
                    }
                }

                if (!file.isEmpty()) {
                    files.add(file);
                }
            }
        }

        return files;
    }

    /**
     * Get raw products XML response for debugging
     */
    public String getRawProductsXML(String categorySlug) {
        if (!tekraApiEnabled) {
            return "Tekra API is disabled";
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .queryParam("action", "browse")
                    .queryParam("catSlug", categorySlug)
                    .queryParam("page", 1)
                    .queryParam("perPage", 5)
                    .queryParam("allProducts", 0)
                    .queryParam("in_stock", 1)
                    .queryParam("out_of_stock", 1)
                    .queryParam("order", "bestsellers")
                    .queryParam("feed", 1)
                    .queryParam("access_token_feed", accessToken)
                    .toUriString();

            return restTemplate.getForObject(url, String.class);

        } catch (Exception e) {
            log.error("Error getting raw products XML", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Get all products for a category with pagination support
     * (Add this method to TekraApiService.java)
     */
    public List<Map<String, Object>> getAllProductsForCategory(String categorySlug) {
        if (!tekraApiEnabled) {
            log.warn("Tekra API is disabled");
            return new ArrayList<>();
        }

        List<Map<String, Object>> allProducts = new ArrayList<>();
        int page = 1;
        int perPage = 100; // Fetch 100 products per page
        boolean hasMore = true;

        try {
            log.info("Fetching all products for category: {} with pagination", categorySlug);

            while (hasMore) {
                String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                        .queryParam("action", "browse")
                        .queryParam("catSlug", categorySlug)
                        .queryParam("page", page)
                        .queryParam("perPage", perPage)
                        .queryParam("allProducts", 0)
                        .queryParam("in_stock", 1)
                        .queryParam("out_of_stock", 1)
                        .queryParam("order", "bestsellers")
                        .queryParam("feed", 1)
                        .queryParam("access_token_feed", accessToken)
                        .toUriString();

                String xmlResponse = restTemplate.getForObject(url, String.class);

                if (xmlResponse == null) {
                    log.error("Received null XML response from Tekra API for page {}", page);
                    break;
                }

                List<Map<String, Object>> pageProducts = parseProductsFromXML(xmlResponse);

                if (pageProducts.isEmpty()) {
                    log.info("No more products found on page {}", page);
                    hasMore = false;
                } else {
                    allProducts.addAll(pageProducts);
                    log.info("Fetched {} products from page {} (total so far: {})",
                            pageProducts.size(), page, allProducts.size());

                    // If we got fewer products than perPage, we're on the last page
                    if (pageProducts.size() < perPage) {
                        hasMore = false;
                    } else {
                        page++;
                        // Small delay to avoid overwhelming the API
                        Thread.sleep(200);
                    }
                }
            }

            log.info("Fetched total of {} products for category: {}", allProducts.size(), categorySlug);
            return allProducts;

        } catch (Exception e) {
            log.error("Error fetching all products for category {}", categorySlug, e);
            return allProducts; // Return what we managed to fetch
        }
    }

    /**
     * Clear the products cache (call this before starting a full sync)
     */
    public void clearCache() {
        productsCache.clear();
        cacheTimestamp = 0;
        log.info("Cleared Tekra API products cache");
    }

    /**
     * Check if cache is still valid
     */
    private boolean isCacheValid() {
        return (System.currentTimeMillis() - cacheTimestamp) < CACHE_DURATION_MS;
    }

    /**
     * Get products with caching support (updated version)
     */
    public List<Map<String, Object>> getProductsRaw(String categorySlug) {
        if (!tekraApiEnabled) {
            log.warn("Tekra API is disabled");
            return new ArrayList<>();
        }

        // Check cache first
        if (isCacheValid() && productsCache.containsKey(categorySlug)) {
            log.debug("Returning cached products for category: {}", categorySlug);
            return productsCache.get(categorySlug);
        }

        try {
            log.info("Fetching products for category: {} (XML parsing)", categorySlug);

            String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .queryParam("action", "browse")
                    .queryParam("catSlug", categorySlug)
                    .queryParam("page", 1)
                    .queryParam("perPage", 100)
                    .queryParam("allProducts", 0)
                    .queryParam("in_stock", 1)
                    .queryParam("out_of_stock", 1)
                    .queryParam("order", "bestsellers")
                    .queryParam("feed", 1)
                    .queryParam("access_token_feed", accessToken)
                    .toUriString();

            String xmlResponse = restTemplate.getForObject(url, String.class);

            if (xmlResponse == null) {
                log.error("Received null XML response from Tekra API for products");
                return new ArrayList<>();
            }

            List<Map<String, Object>> products = parseProductsFromXML(xmlResponse);

            // Update cache
            productsCache.put(categorySlug, products);
            cacheTimestamp = System.currentTimeMillis();

            log.info("Extracted {} products from Tekra XML response (cached)", products.size());
            return products;

        } catch (Exception e) {
            log.error("Error fetching products from Tekra API", e);
            return new ArrayList<>();
        }
    }
}