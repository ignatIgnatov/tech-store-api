package com.techstore.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@RestController
@RequestMapping("/api/tekra-debug")
@RequiredArgsConstructor
@Slf4j
public class TekraDebugController {

    private final RestTemplate restTemplate;

    @Value("${tekra.api.access-token}")
    private String accessToken;

    @GetMapping("/raw-response")
    public ResponseEntity<String> getRawResponse() {
        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://tekra.bg/shop/api")
                    .queryParam("action", "categories")
                    .queryParam("access_token_feed", accessToken)
                    .toUriString();

            log.info("Making request to: {}", url.replaceAll(accessToken, "***TOKEN***"));

            // Get raw string response to see actual structure
            String response = restTemplate.getForObject(url, String.class);

            log.info("Raw API response: {}", response);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting raw response", e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/test-products")
    public ResponseEntity<String> testProductsEndpoint() {
        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://tekra.bg/shop/api")
                    .queryParam("action", "browse")
                    .queryParam("catSlug", "videonablyudenie")
                    .queryParam("page", 1)
                    .queryParam("perPage", 5)
                    .queryParam("allProducts", 0)
                    .queryParam("in_stock", 1)
                    .queryParam("out_of_stock", 1)
                    .queryParam("order", "bestsellers")
                    .queryParam("feed", 1)
                    .queryParam("access_token_feed", accessToken)
                    .toUriString();

            log.info("Testing products endpoint: {}", url.replaceAll(accessToken, "***TOKEN***"));

            String response = restTemplate.getForObject(url, String.class);
            log.info("Products API response: {}", response);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error testing products endpoint", e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/test-simple")
    public ResponseEntity<Map<String, Object>> testSimpleCall() {
        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://tekra.bg/shop/api")
                    .queryParam("action", "categories")
                    .queryParam("access_token_feed", accessToken)
                    .toUriString();

            // Try to get as Map to see structure
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            log.info("Parsed API response structure: {}", response);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error with simple call", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}