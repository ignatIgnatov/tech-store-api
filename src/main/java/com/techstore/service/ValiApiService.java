package com.techstore.service;

import com.techstore.dto.external.ExternalCategoryDto;
import com.techstore.dto.external.ExternalManufacturerDto;
import com.techstore.dto.external.ExternalParameterDto;
import com.techstore.dto.external.ExternalProductDto;
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

    public List<ExternalCategoryDto> getCategories() {
        log.debug("Fetching categories from external API");

        return webClient.get()
                .uri(baseUrl + "/categories")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ExternalCategoryDto>>() {
                })
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
                .bodyToMono(new ParameterizedTypeReference<List<ExternalManufacturerDto>>() {
                })
                .timeout(Duration.ofMillis(timeout))
                .retryWhen(Retry.backoff(retryAttempts, Duration.ofMillis(retryDelay)))
                .doOnError(WebClientResponseException.class, ex ->
                        log.error("Error fetching manufacturers: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString()))
                .block();
    }

    public List<ExternalParameterDto> getParametersByCategory(Long categoryId) {
        log.debug("Fetching parameters for category: {}", categoryId);

        try {
            List<ExternalParameterDto> parameters = webClient.get()
                    .uri(baseUrl + "/parameters/" + categoryId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<ExternalParameterDto>>() {
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

    public List<ExternalProductDto> getProductsByCategory(Long categoryId) {
        log.debug("Fetching products for category: {}", categoryId);

        List<ExternalProductDto> products = webClient.get()
                .uri(baseUrl + "/products/by_category/" + categoryId + "/full")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ExternalProductDto>>() {
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
}