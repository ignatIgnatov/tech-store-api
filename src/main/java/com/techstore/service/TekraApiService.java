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

    @Value("${tekra.api.timeout:30000}")
    private int timeout;

    @Value("${tekra.api.retry-attempts:3}")
    private int retryAttempts;

    @Value("${tekra.api.retry-delay:2000}")
    private long retryDelay;

    /**
     * Fetches all video surveillance products from Tekra API with proper pagination
     */
    public TekraProductFeed getVideoSurveillanceProducts() {
        log.info("Fetching video surveillance products from Tekra API with pagination");

        TekraProductFeed combinedFeed = new TekraProductFeed();
        combinedFeed.setProducts(new ArrayList<>());

        int currentPage = 1;
        int totalPages = 1;
        int totalProductsFetched = 0;

        try {
            do {
                log.debug("Fetching page {} of {} from Tekra API", currentPage, totalPages);

                int finalCurrentPage = currentPage;
                int finalCurrentPage1 = currentPage;
                String xmlResponse = webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .scheme("https")
                                .host("tekra.bg")
                                .path("/shop/api")
                                .queryParam("s", "")
                                .queryParam("catSlug", "videonablyudenie")
                                .queryParam("subCatSlug", "")
                                .queryParam("subSubCatSlug", "")
                                .queryParam("tagSlug", "")
                                .queryParam("in_promo", "0")
                                .queryParam("in_stock", "1")
                                .queryParam("out_of_stock", "1")
                                .queryParam("order", "bestsellers")
                                .queryParam("page", String.valueOf(finalCurrentPage))
                                .queryParam("perPage", "100") // Use reasonable page size
                                .queryParam("allProducts", "0") // Keep original setting
                                .queryParam("forWin", "0")
                                .queryParam("feed_format", "0")
                                .queryParam("action", "browse")
                                .queryParam("feed", "1")
                                .queryParam("access_token_feed", accessToken)
                                .build())
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofMillis(timeout))
                        .retryWhen(Retry.backoff(retryAttempts, Duration.ofMillis(retryDelay)))
                        .doOnError(WebClientResponseException.class, ex ->
                                log.error("Error fetching Tekra products page {}: {} - {}",
                                        finalCurrentPage1, ex.getStatusCode(), ex.getResponseBodyAsString()))
                        .block();

                if (xmlResponse == null || xmlResponse.isEmpty()) {
                    log.warn("Empty response from Tekra API for page {}", currentPage);
                    break;
                }

                // Parse XML response
                TekraProductFeed pageFeed = xmlMapper.readValue(xmlResponse, TekraProductFeed.class);

                if (pageFeed.getProducts() != null && !pageFeed.getProducts().isEmpty()) {
                    combinedFeed.getProducts().addAll(pageFeed.getProducts());
                    totalProductsFetched += pageFeed.getProducts().size();
                    log.debug("Fetched {} products from page {}", pageFeed.getProducts().size(), currentPage);
                }

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

                currentPage++;

                // Add small delay between pages to be respectful to the API
                if (currentPage <= totalPages) {
                    Thread.sleep(500);
                }

            } while (currentPage <= totalPages);

            combinedFeed.setTotalPages(totalPages);
            log.info("Successfully fetched {} products across {} pages from Tekra",
                    totalProductsFetched, totalPages);

            return combinedFeed;

        } catch (Exception e) {
            log.error("Error fetching video surveillance products from Tekra: {}", e.getMessage(), e);
            return combinedFeed; // Return what we have so far
        }
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

        log.info("Found {} Wildlife Surveillance products out of {} total products",
                wildlifeProducts.size(), feed.getProducts().size());

        return wildlifeProducts;
    }

    /**
     * Gets all unique categories from the feed
     */
    public List<TekraCategory> getCategories() {
        log.info("Extracting categories from Tekra products");

        TekraProductFeed feed = getVideoSurveillanceProducts();

        if (feed.getProducts() == null) {
            return List.of();
        }

        // Extract unique categories
        return feed.getProducts().stream()
                .filter(product -> product.getCategory() != null)
                .map(TekraProduct::getCategory)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Gets all unique parameters from Wildlife Surveillance products
     */
    public List<TekraParameter> getWildlifeSurveillanceParameters() {
        log.info("Extracting parameters from Wildlife Surveillance products");

        List<TekraProduct> products = getWildlifeSurveillanceProducts();

        return products.stream()
                .filter(product -> product.getParameters() != null)
                .flatMap(product -> product.getParameters().stream())
                .collect(Collectors.toMap(
                        param -> param.getName() + "_" + param.getType(),
                        param -> param,
                        (existing, replacement) -> existing)) // Keep first occurrence
                .values()
                .stream()
                .collect(Collectors.toList());
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

    /**
     * Test connection to Tekra API
     */
    public boolean testConnection() {
        log.info("Testing connection to Tekra API");

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("tekra.bg")
                            .path("/shop/api")
                            .queryParam("action", "browse")
                            .queryParam("feed", "1")
                            .queryParam("perPage", "1")
                            .queryParam("access_token_feed", accessToken)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(10000))
                    .block();

            boolean isConnected = response != null && !response.isEmpty();
            log.info("Tekra API connection test: {}", isConnected ? "SUCCESS" : "FAILED");
            return isConnected;

        } catch (Exception e) {
            log.error("Tekra API connection test failed: {}", e.getMessage());
            return false;
        }
    }
}