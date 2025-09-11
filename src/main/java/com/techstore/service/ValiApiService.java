package com.techstore.service;

import com.techstore.dto.external.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
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

    public List<ExternalCategoryDto> getCategories() {
        log.debug("Fetching categories from external API");

        return webClient.get()
                .uri(baseUrl + "/categories")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ExternalCategoryDto>>() {})
                .timeout(Duration.ofMillis(timeout))
                .retryWhen(Retry.backoff(retryAttempts, Duration.ofMillis(retryDelay)))
                .doOnError(WebClientResponseException.class, ex ->
                        log.error("Error fetching categories: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString()))
                .block();
    }

    public List<ExternalManufacturerDto> getManufacturers() {
        log.debug("Fetching manufacturers from external API");

        return webClient.get()
                .uri(baseUrl + "/manufacturers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ExternalManufacturerDto>>() {})
                .timeout(Duration.ofMillis(timeout))
                .retryWhen(Retry.backoff(retryAttempts, Duration.ofMillis(retryDelay)))
                .doOnError(WebClientResponseException.class, ex ->
                        log.error("Error fetching manufacturers: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString()))
                .block();
    }

    public List<ExternalParameterDto> getParametersByCategory(Long categoryId) {
        log.debug("Fetching parameters for category: {}", categoryId);

        return webClient.get()
                .uri(baseUrl + "/parameters/" + categoryId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ExternalParameterDto>>() {})
                .timeout(Duration.ofMillis(timeout))
                .retryWhen(Retry.backoff(retryAttempts, Duration.ofMillis(retryDelay)))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.warn("Error fetching parameters for category {}: {} - {}",
                            categoryId, ex.getStatusCode(), ex.getResponseBodyAsString());
                    return Mono.just(List.of());
                })
                .block();
    }

    public PaginatedProductsDto getProducts(int page, int perPage) {
        log.debug("Fetching products page: {}, perPage: {}", page, perPage);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(baseUrl + "/products/full")
                        .queryParam("page", page)
                        .queryParam("per_page", perPage)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(PaginatedProductsDto.class)
                .timeout(Duration.ofMillis(timeout))
                .retryWhen(Retry.backoff(retryAttempts, Duration.ofMillis(retryDelay)))
                .doOnError(WebClientResponseException.class, ex ->
                        log.error("Error fetching products page {}: {} - {}",
                                page, ex.getStatusCode(), ex.getResponseBodyAsString()))
                .block();
    }

    public ExternalProductDto getProduct(Long productId) {
        log.debug("Fetching product: {}", productId);

        return webClient.get()
                .uri(baseUrl + "/product/" + productId + "/full")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(ExternalProductDto.class)
                .timeout(Duration.ofMillis(timeout))
                .retryWhen(Retry.backoff(retryAttempts, Duration.ofMillis(retryDelay)))
                .doOnError(WebClientResponseException.class, ex ->
                        log.error("Error fetching product {}: {} - {}",
                                productId, ex.getStatusCode(), ex.getResponseBodyAsString()))
                .block();
    }
}