package com.techstore.service;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.techstore.dto.tekra.TekraCategory;
import com.techstore.dto.tekra.TekraParameter;
import com.techstore.dto.tekra.TekraProduct;
import com.techstore.dto.tekra.TekraProductFeed;
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
     * Fetches ALL video surveillance products from Tekra API with proper pagination
     */
    public TekraProductFeed getAllVideoSurveillanceProducts() {
        log.info("Fetching ALL video surveillance products from Tekra API with pagination");

        TekraProductFeed combinedFeed = new TekraProductFeed();
        combinedFeed.setProducts(new ArrayList<>());

        int currentPage = 1;
        int totalPages = 1;
        int totalProductsFetched = 0;

        try {
            do {
                log.debug("Fetching page {} of {} from Tekra API", currentPage, totalPages);

                int finalCurrentPage = currentPage;
                String xmlResponse = webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .scheme("https")
                                .host("tekra.bg")
                                .path("/shop/api")
                                .queryParam("s", "")
                                .queryParam("catSlug", "videonablyudenie") // Main video surveillance category
                                .queryParam("subCatSlug", "")
                                .queryParam("subSubCatSlug", "")
                                .queryParam("tagSlug", "")
                                .queryParam("in_promo", "0")
                                .queryParam("in_stock", "1")
                                .queryParam("out_of_stock", "1")
                                .queryParam("order", "bestsellers")
                                .queryParam("page", String.valueOf(finalCurrentPage))
                                .queryParam("perPage", "100") // Use reasonable page size
                                .queryParam("allProducts", "1") // Changed to 1 to get ALL products
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
                                        finalCurrentPage, ex.getStatusCode(), ex.getResponseBodyAsString()))
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

            } while (currentPage <= totalPages && totalPages <= 50); // Safety limit

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
     * Returns ALL video surveillance products (no filtering)
     */
    public List<TekraProduct> getAllVideoSurveillanceProductsList() {
        log.info("Getting ALL video surveillance products");

        TekraProductFeed feed = getAllVideoSurveillanceProducts();

        if (feed.getProducts() == null) {
            return List.of();
        }

        log.info("Found {} total video surveillance products", feed.getProducts().size());
        return feed.getProducts();
    }

    /**
     * Gets all unique categories from ALL video surveillance products
     */
    public List<TekraCategory> getAllVideoSurveillanceCategories() {
        log.info("Extracting categories from ALL video surveillance products");

        TekraProductFeed feed = getAllVideoSurveillanceProducts();

        if (feed.getProducts() == null) {
            return List.of();
        }

        // Extract unique categories
        List<TekraCategory> uniqueCategories = feed.getProducts().stream()
                .filter(product -> product.getCategory() != null)
                .map(TekraProduct::getCategory)
                .distinct()
                .collect(Collectors.toList());

        log.info("Found {} unique categories from video surveillance products", uniqueCategories.size());
        uniqueCategories.forEach(cat -> log.debug("Category: {} (ID: {})", cat.getName(), cat.getId()));

        return uniqueCategories;
    }

    /**
     * Gets all unique parameters from ALL video surveillance products
     */
    public List<TekraParameter> getAllVideoSurveillanceParameters() {
        log.info("Extracting parameters from ALL video surveillance products");

        List<TekraProduct> products = getAllVideoSurveillanceProductsList();

        List<TekraParameter> uniqueParameters = products.stream()
                .filter(product -> product.getParameters() != null)
                .flatMap(product -> product.getParameters().stream())
                .collect(Collectors.toMap(
                        param -> param.getId() + "_" + param.getName() + "_" + param.getType(),
                        param -> param,
                        (existing, replacement) -> existing)) // Keep first occurrence
                .values()
                .stream()
                .toList();

        log.info("Found {} unique parameters from video surveillance products", uniqueParameters.size());
        return uniqueParameters;
    }
}