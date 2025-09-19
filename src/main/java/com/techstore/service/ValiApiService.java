package com.techstore.service;

import com.techstore.dto.request.CategoryRequestFromExternalDto;
import com.techstore.dto.request.DocumentRequestDto;
import com.techstore.dto.request.ManufacturerRequestDto;
import com.techstore.dto.request.ParameterRequestDto;
import com.techstore.dto.request.ProductRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValiApiService {

    @Qualifier("largeResponseWebClient")
    private final WebClient webClient;

    @Value("${vali.api.base-url}")
    private String baseUrl;

    @Value("${vali.api.token}")
    private String apiToken;

    @Value("${vali.api.timeout}")
    private int timeout;

    @Value("${vali.api.retry-attempts}")
    private int retryAttempts;

    @Value("${vali.api.retry-delay}")
    private long retryDelay;

    public List<CategoryRequestFromExternalDto> getCategories() {
        log.debug("Fetching categories from external API");

        return webClient.get()
                .uri(baseUrl + "/categories")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<CategoryRequestFromExternalDto>>() {
                })
                .timeout(Duration.ofMillis(timeout))
                .retryWhen(Retry.backoff(retryAttempts, Duration.ofMillis(retryDelay)))
                .doOnError(WebClientResponseException.class, ex ->
                        log.error("Error fetching categories: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString()))
                .block();
    }

    public List<ManufacturerRequestDto> getManufacturers() {
        log.debug("Fetching manufacturers from external API");

        return webClient.get()
                .uri(baseUrl + "/manufacturers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ManufacturerRequestDto>>() {
                })
                .timeout(Duration.ofMillis(timeout))
                .retryWhen(Retry.backoff(retryAttempts, Duration.ofMillis(retryDelay)))
                .doOnError(WebClientResponseException.class, ex ->
                        log.error("Error fetching manufacturers: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString()))
                .block();
    }

    public List<ParameterRequestDto> getParametersByCategory(Long categoryId) {
        log.debug("Fetching parameters for category: {}", categoryId);

        try {
            List<ParameterRequestDto> parameters = webClient.get()
                    .uri(baseUrl + "/parameters/" + categoryId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<ParameterRequestDto>>() {
                    })
                    .timeout(Duration.ofMillis(timeout))
                    .retryWhen(Retry.backoff(retryAttempts, Duration.ofMillis(retryDelay)))
                    .onErrorResume(DataBufferLimitException.class, ex -> {
                        log.error("Response too large for category {}: {}", categoryId, ex.getMessage());
                        return Mono.just(List.of());
                    })
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        log.warn("Error fetching parameters for category {}: {} - {}",
                                categoryId, ex.getStatusCode(), ex.getResponseBodyAsString());
                        return Mono.just(List.of());
                    })
                    .block();

            return parameters != null ? parameters : List.of();

        } catch (Exception e) {
            log.error("Unexpected error fetching parameters for category {}: {}", categoryId, e.getMessage());
            return List.of();
        }
    }

    public List<ProductRequestDto> getProductsByCategory(Long categoryId) {
        log.debug("Fetching products for category: {}", categoryId);

        List<ProductRequestDto> products = webClient.get()
                .uri(baseUrl + "/products/by_category/" + categoryId + "/full")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ProductRequestDto>>() {
                })
                .timeout(Duration.ofMillis(timeout))
                .retryWhen(Retry.backoff(retryAttempts, Duration.ofMillis(retryDelay)))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.warn("Error fetching products for category {}: {} - {}",
                            categoryId, ex.getStatusCode(), ex.getResponseBodyAsString());
                    return Mono.just(List.of());
                })
                .block();

        if (products == null) {
            log.warn("No products found for category {}", categoryId);
            return List.of();
        }

        log.debug("Found {} products for category {}", products.size(), categoryId);
        return products;
    }

    public List<DocumentRequestDto> getDocumentsByProduct(Long productId) {
        log.debug("Fetching documents for product: {}", productId);

        try {
            List<DocumentRequestDto> documents = webClient.get()
                    .uri(baseUrl + "/products/" + productId + "/documents")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<DocumentRequestDto>>() {
                    })
                    .timeout(Duration.ofMillis(timeout))
                    .retryWhen(Retry.backoff(retryAttempts, Duration.ofMillis(retryDelay)))
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        log.warn("Error fetching documents for product {}: {} - {}",
                                productId, ex.getStatusCode(), ex.getResponseBodyAsString());
                        return Mono.just(List.of());
                    })
                    .block();

            if (documents == null) {
                log.debug("No documents found for product {}", productId);
                return List.of();
            }

            log.debug("Found {} documents for product {}", documents.size(), productId);
            return documents;

        } catch (Exception e) {
            log.error("Unexpected error fetching documents for product {}: {}", productId, e.getMessage());
            return List.of();
        }
    }

    public List<DocumentRequestDto> getAllDocuments() {
        log.debug("Fetching all documents from external API");

        try {
            List<DocumentRequestDto> documents = webClient.get()
                    .uri(baseUrl + "/documents")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<DocumentRequestDto>>() {
                    })
                    .timeout(Duration.ofMillis(timeout))
                    .retryWhen(Retry.backoff(retryAttempts, Duration.ofMillis(retryDelay)))
                    .onErrorResume(DataBufferLimitException.class, ex -> {
                        log.error("Response too large for documents: {}", ex.getMessage());
                        return Mono.just(List.of());
                    })
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        log.warn("Error fetching all documents: {} - {}",
                                ex.getStatusCode(), ex.getResponseBodyAsString());
                        return Mono.just(List.of());
                    })
                    .block();

            if (documents == null) {
                log.debug("No documents found");
                return List.of();
            }

            log.debug("Found {} total documents", documents.size());
            return documents;

        } catch (Exception e) {
            log.error("Unexpected error fetching all documents: {}", e.getMessage());
            return List.of();
        }
    }
}