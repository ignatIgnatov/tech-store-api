package com.techstore.service;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.techstore.dto.tekra.TekraProductFeed;
import com.techstore.dto.tekra.TekraProduct;
import com.techstore.dto.tekra.TekraCategory;
import com.techstore.dto.tekra.TekraParameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TekraApiService {

    private final WebClient webClient;
    private final XmlMapper xmlMapper;

    @Value("${tekra.api.base-url:https://tekra.bg/shop/api}")
    private String baseUrl;

    @Value("${tekra.api.access-token}")
    private String accessToken;

    @Value("${tekra.api.timeout:60000}")
    private int timeout;

    @Value("${tekra.api.retry-attempts:3}")
    private int retryAttempts;

    @Value("${tekra.api.retry-delay:2000}")
    private long retryDelay;

    @Value("${tekra.api.per-page:100}")
    private int perPageLimit;

    /**
     * Fetches ALL products from Tekra API across all categories
     */
    public TekraProductFeed getAllProducts() {
        log.info("Fetching ALL products from Tekra API");

        TekraProductFeed combinedFeed = new TekraProductFeed();
        combinedFeed.setProducts(new ArrayList<>());

        int currentPage = 1;
        int totalPages = 1;
        int totalProductsFetched = 0;

        try {
            do {
                log.debug("Fetching page {} of {} from Tekra API (all products)", currentPage, totalPages);

                TekraProductFeed pageFeed = fetchProductPage(currentPage, null, null, null);

                if (pageFeed != null && pageFeed.getProducts() != null && !pageFeed.getProducts().isEmpty()) {
                    combinedFeed.getProducts().addAll(pageFeed.getProducts());
                    totalProductsFetched += pageFeed.getProducts().size();
                    log.debug("Fetched {} products from page {}", pageFeed.getProducts().size(), currentPage);

                    // Update pagination info from first page
                    if (currentPage == 1) {
                        combinedFeed.setTotalProducts(pageFeed.getTotalProducts());
                        combinedFeed.setCurrentPage(pageFeed.getCurrentPage());
                        totalPages = pageFeed.getTotalPages() != null ? pageFeed.getTotalPages() : 1;

                        log.info("Tekra API reports {} total products across {} pages",
                                pageFeed.getTotalProducts(), totalPages);

                        // Set categories from first page
                        combinedFeed.setCategories(pageFeed.getCategories());
                    }
                } else {
                    log.warn("Empty response from Tekra API for page {}", currentPage);
                    break;
                }

                currentPage++;

                // Add delay between pages to be respectful to the API
                if (currentPage <= totalPages) {
                    Thread.sleep(500);
                }

            } while (currentPage <= totalPages);

            combinedFeed.setTotalPages(totalPages);
            log.info("Successfully fetched {} products across {} pages from Tekra",
                    totalProductsFetched, totalPages);

            return combinedFeed;

        } catch (Exception e) {
            log.error("Error fetching all products from Tekra: {}", e.getMessage(), e);
            return combinedFeed; // Return what we have so far
        }
    }

    /**
     * Fetches products by specific category slug
     */
    public TekraProductFeed getProductsByCategory(String categorySlug) {
        log.info("Fetching products for category: {}", categorySlug);

        TekraProductFeed combinedFeed = new TekraProductFeed();
        combinedFeed.setProducts(new ArrayList<>());

        int currentPage = 1;
        int totalPages = 1;
        int totalProductsFetched = 0;

        try {
            do {
                log.debug("Fetching page {} for category {} from Tekra API", currentPage, categorySlug);

                TekraProductFeed pageFeed = fetchProductPage(currentPage, categorySlug, null, null);

                if (pageFeed != null && pageFeed.getProducts() != null && !pageFeed.getProducts().isEmpty()) {
                    combinedFeed.getProducts().addAll(pageFeed.getProducts());
                    totalProductsFetched += pageFeed.getProducts().size();

                    // Update pagination info from first page
                    if (currentPage == 1) {
                        combinedFeed.setTotalProducts(pageFeed.getTotalProducts());
                        totalPages = pageFeed.getTotalPages() != null ? pageFeed.getTotalPages() : 1;
                        combinedFeed.setCategories(pageFeed.getCategories());
                    }
                } else {
                    break;
                }

                currentPage++;
                if (currentPage <= totalPages) {
                    Thread.sleep(500);
                }

            } while (currentPage <= totalPages);

            log.info("Successfully fetched {} products for category {} across {} pages",
                    totalProductsFetched, categorySlug, totalPages);

            return combinedFeed;

        } catch (Exception e) {
            log.error("Error fetching products for category {}: {}", categorySlug, e.getMessage(), e);
            return combinedFeed;
        }
    }

    /**
     * Fetches video surveillance products specifically
     */
    public TekraProductFeed getVideoSurveillanceProducts() {
        return getProductsByCategory("videonablyudenie");
    }

    /**
     * Filters products for Wildlife Surveillance category only
     */
    public List<TekraProduct> getWildlifeSurveillanceProducts() {
        log.info("Fetching Wildlife Surveillance products specifically");

        TekraProductFeed feed = getVideoSurveillanceProducts();

        if (feed.getProducts() == null) {
            return List.of();
        }

        // Filter for Wildlife Surveillance products
        List<TekraProduct> wildlifeProducts = feed.getProducts().stream()
                .filter(this::isWildlifeSurveillanceProduct)
                .collect(Collectors.toList());

        log.info("Found {} Wildlife Surveillance products out of {} total video surveillance products",
                wildlifeProducts.size(), feed.getProducts().size());

        return wildlifeProducts;
    }

    /**
     * Gets products with stock and promotion filters
     */
    public TekraProductFeed getProductsWithFilters(String categorySlug, String subCategorySlug,
                                                   boolean inPromo, boolean inStock, boolean outOfStock,
                                                   String orderBy) {
        log.info("Fetching products with filters - category: {}, subCategory: {}, inPromo: {}, inStock: {}, outOfStock: {}, orderBy: {}",
                categorySlug, subCategorySlug, inPromo, inStock, outOfStock, orderBy);

        TekraProductFeed combinedFeed = new TekraProductFeed();
        combinedFeed.setProducts(new ArrayList<>());

        int currentPage = 1;
        int totalPages = 1;
        int totalProductsFetched = 0;

        try {
            do {
                TekraProductFeed pageFeed = fetchProductPageWithFilters(currentPage, categorySlug, subCategorySlug,
                        inPromo, inStock, outOfStock, orderBy);

                if (pageFeed != null && pageFeed.getProducts() != null && !pageFeed.getProducts().isEmpty()) {
                    combinedFeed.getProducts().addAll(pageFeed.getProducts());
                    totalProductsFetched += pageFeed.getProducts().size();

                    if (currentPage == 1) {
                        combinedFeed.setTotalProducts(pageFeed.getTotalProducts());
                        totalPages = pageFeed.getTotalPages() != null ? pageFeed.getTotalPages() : 1;
                        combinedFeed.setCategories(pageFeed.getCategories());
                    }
                } else {
                    break;
                }

                currentPage++;
                if (currentPage <= totalPages) {
                    Thread.sleep(500);
                }

            } while (currentPage <= totalPages);

            log.info("Successfully fetched {} filtered products across {} pages", totalProductsFetched, totalPages);
            return combinedFeed;

        } catch (Exception e) {
            log.error("Error fetching filtered products: {}", e.getMessage(), e);
            return combinedFeed;
        }
    }

    /**
     * Gets all unique categories from all products
     */
    public List<TekraCategory> getAllCategories() {
        log.info("Extracting all categories from Tekra products");

        TekraProductFeed feed = getAllProducts();

        if (feed.getProducts() == null) {
            return List.of();
        }

        // Extract unique categories
        List<TekraCategory> categories = feed.getProducts().stream()
                .filter(product -> product.getCategory() != null)
                .map(TekraProduct::getCategory)
                .distinct()
                .collect(Collectors.toList());

        log.info("Found {} unique categories", categories.size());
        return categories;
    }

    /**
     * Gets all unique parameters from all products
     */
    public List<TekraParameter> getAllParameters() {
        log.info("Extracting all parameters from Tekra products");

        TekraProductFeed feed = getAllProducts();

        if (feed.getProducts() == null) {
            return List.of();
        }

        List<TekraParameter> parameters = feed.getProducts().stream()
                .filter(product -> product.getParameters() != null)
                .flatMap(product -> product.getParameters().stream())
                .collect(Collectors.toMap(
                        param -> param.getName() + "_" + param.getType() + "_" + (param.getId() != null ? param.getId() : ""),
                        param -> param,
                        (existing, replacement) -> existing)) // Keep first occurrence
                .values()
                .stream()
                .collect(Collectors.toList());

        log.info("Found {} unique parameters across all products", parameters.size());
        return parameters;
    }

    /**
     * Gets parameters for a specific category
     */
    public List<TekraParameter> getParametersByCategory(String categorySlug) {
        log.info("Extracting parameters for category: {}", categorySlug);

        TekraProductFeed feed = getProductsByCategory(categorySlug);

        if (feed.getProducts() == null) {
            return List.of();
        }

        return feed.getProducts().stream()
                .filter(product -> product.getParameters() != null)
                .flatMap(product -> product.getParameters().stream())
                .collect(Collectors.toMap(
                        param -> param.getName() + "_" + param.getType(),
                        param -> param,
                        (existing, replacement) -> existing))
                .values()
                .stream()
                .collect(Collectors.toList());
    }

    /**
     * Gets product count for a specific category
     */
    public int getProductCountByCategory(String categorySlug) {
        try {
            TekraProductFeed firstPage = fetchProductPage(1, categorySlug, null, null);
            return firstPage != null && firstPage.getTotalProducts() != null ? firstPage.getTotalProducts() : 0;
        } catch (Exception e) {
            log.error("Error getting product count for category {}: {}", categorySlug, e.getMessage());
            return 0;
        }
    }

    /**
     * Test connection to Tekra API
     */
    public boolean testConnection() {
        log.info("Testing connection to Tekra API");

        try {
            TekraProductFeed response = fetchProductPage(1, null, null, null);
            boolean isConnected = response != null && response.getProducts() != null;
            log.info("Tekra API connection test: {}", isConnected ? "SUCCESS" : "FAILED");
            return isConnected;

        } catch (Exception e) {
            log.error("Tekra API connection test failed: {}", e.getMessage());
            return false;
        }
    }

    // ============ PRIVATE HELPER METHODS ============

    /**
     * Fetches a single page of products
     */
    private TekraProductFeed fetchProductPage(int page, String categorySlug, String subCategorySlug, String tagSlug) {
        return fetchProductPageWithFilters(page, categorySlug, subCategorySlug, false, true, true, "bestsellers");
    }

    /**
     * Fetches a single page of products with detailed filters
     */
    private TekraProductFeed fetchProductPageWithFilters(int page, String categorySlug, String subCategorySlug,
                                                         boolean inPromo, boolean inStock, boolean outOfStock,
                                                         String orderBy) {
        try {
            String xmlResponse = webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .scheme("https")
                                .host("tekra.bg")
                                .path("/shop/api")
                                .queryParam("s", "")
                                .queryParam("catSlug", categorySlug != null ? categorySlug : "")
                                .queryParam("subCatSlug", subCategorySlug != null ? subCategorySlug : "")
                                .queryParam("subSubCatSlug", "")
                                .queryParam("tagSlug", "")
                                .queryParam("in_promo", inPromo ? "1" : "0")
                                .queryParam("in_stock", inStock ? "1" : "0")
                                .queryParam("out_of_stock", outOfStock ? "1" : "0")
                                .queryParam("order", orderBy != null ? orderBy : "bestsellers")
                                .queryParam("page", String.valueOf(page))
                                .queryParam("perPage", String.valueOf(perPageLimit))
                                .queryParam("allProducts", "0")
                                .queryParam("forWin", "0")
                                .queryParam("feed_format", "0")
                                .queryParam("action", "browse")
                                .queryParam("feed", "1")
                                .queryParam("access_token_feed", accessToken);
                        return builder.build();
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeout))
                    .retryWhen(Retry.backoff(retryAttempts, Duration.ofMillis(retryDelay)))
                    .doOnError(WebClientResponseException.class, ex ->
                            log.error("Error fetching Tekra products page {}: {} - {}",
                                    page, ex.getStatusCode(), ex.getResponseBodyAsString()))
                    .block();

            if (xmlResponse == null || xmlResponse.isEmpty()) {
                log.warn("Empty response from Tekra API for page {}", page);
                return null;
            }

            // Parse XML response
            return xmlMapper.readValue(xmlResponse, TekraProductFeed.class);

        } catch (Exception e) {
            log.error("Error fetching Tekra page {}: {}", page, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Checks if a product belongs to Wildlife Surveillance category
     */
    private boolean isWildlifeSurveillanceProduct(TekraProduct product) {
        if (product.getCategory() == null || product.getName() == null) {
            return false;
        }

        String categoryName = product.getCategory().getName().toLowerCase();
        String productName = product.getName().toLowerCase();

        // Check category and product name for wildlife surveillance keywords
        return categoryName.contains("wildlife") ||
                categoryName.contains("дива природа") ||
                categoryName.contains("ловни") ||
                productName.contains("wildlife") ||
                productName.contains("trail cam") ||
                productName.contains("ловна") ||
                productName.contains("дива природа") ||
                productName.contains("game camera") ||
                // Additional checks for hunting/trail cameras
                (productName.contains("камера") &&
                        (productName.contains("ловна") || productName.contains("trail") ||
                                productName.contains("wildlife") || productName.contains("game")));
    }
}