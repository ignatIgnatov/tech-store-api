package com.techstore.service;

import com.techstore.config.SpeedyConfig;
import com.techstore.dto.speedy.SpeedyAuthenticationRequest;
import com.techstore.dto.speedy.SpeedyAuthenticationResponse;
import com.techstore.dto.speedy.SpeedyCalculatePriceRequest;
import com.techstore.dto.speedy.SpeedyCalculatePriceResponse;
import com.techstore.dto.speedy.SpeedyOffice;
import com.techstore.dto.speedy.SpeedySite;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpeedyService {

    private final RestTemplate restTemplate;
    private final SpeedyConfig speedyConfig;

    private String authToken;

    /**
     * Аутентикира се с Speedy API
     */
    public void authenticate() {
        try {
            SpeedyAuthenticationRequest authRequest = new SpeedyAuthenticationRequest();
            authRequest.setUserName(speedyConfig.getUserName());
            authRequest.setPassword(speedyConfig.getPassword());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<SpeedyAuthenticationRequest> request = new HttpEntity<>(authRequest, headers);

            ResponseEntity<SpeedyAuthenticationResponse> response = restTemplate.postForEntity(
                    speedyConfig.getBaseUrl() + "/identity/token",
                    request,
                    SpeedyAuthenticationResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                authToken = response.getBody().getAccessToken();
                log.info("Successfully authenticated with Speedy API");
            } else {
                log.error("Failed to authenticate with Speedy API: {}", response.getStatusCode());
                throw new RuntimeException("Speedy authentication failed");
            }
        } catch (Exception e) {
            log.error("Error during Speedy authentication: {}", e.getMessage());
            throw new RuntimeException("Failed to authenticate with Speedy API", e);
        }
    }

    /**
     * Взема населени места по име
     */
    public List<SpeedySite> getCitiesByName(String name) {
        ensureAuthenticated();

        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            String url = UriComponentsBuilder.fromHttpUrl(speedyConfig.getBaseUrl() + "/location/site")
                    .queryParam("name", name)
                    .queryParam("countryId", 100) // България
                    .toUriString();

            ResponseEntity<SpeedySite[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    SpeedySite[].class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return Arrays.asList(response.getBody());
            }

            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching sites from Speedy API: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Взема офиси в дадено населено място
     */
    public List<SpeedyOffice> getOfficesByCityId(Long siteId) {
        ensureAuthenticated();

        try {
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            String url = speedyConfig.getBaseUrl() + "/location/office/" + siteId;

            ResponseEntity<SpeedyOffice[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    SpeedyOffice[].class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return Arrays.asList(response.getBody());
            }

            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching offices from Speedy API: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Изчислява цена на доставка
     */
    public SpeedyCalculatePriceResponse calculateShippingPrice(Long receiverSiteId, BigDecimal totalWeight, Integer parcelCount) {
        ensureAuthenticated();

        try {
            SpeedyCalculatePriceRequest priceRequest = new SpeedyCalculatePriceRequest();
            priceRequest.setSenderSiteId(speedyConfig.getSenderSiteId());
            priceRequest.setReceiverSiteId(receiverSiteId);
            priceRequest.setSaturdayDelivery(speedyConfig.getSaturdayDelivery());

            // Конфигуриране на услугата
            SpeedyCalculatePriceRequest.Service service = new SpeedyCalculatePriceRequest.Service();
            service.setServiceId(speedyConfig.getDefaultServiceId());
            priceRequest.setService(service);

            // Конфигуриране на пратките
            SpeedyCalculatePriceRequest.Parcel parcel = new SpeedyCalculatePriceRequest.Parcel();
            parcel.setWeight(totalWeight);
            parcel.setCount(parcelCount);

            SpeedyCalculatePriceRequest.Content content = new SpeedyCalculatePriceRequest.Content();
            content.setParcels(Collections.singletonList(parcel));
            content.setDocuments(false);
            content.setTotalWeight(totalWeight);
            priceRequest.setContent(content);

            // Конфигуриране на плащача
            SpeedyCalculatePriceRequest.Payer payer = new SpeedyCalculatePriceRequest.Payer();
            payer.setType("SENDER"); // или "RECIPIENT"
            priceRequest.setPayer(payer);

            HttpHeaders headers = createAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<SpeedyCalculatePriceRequest> request = new HttpEntity<>(priceRequest, headers);

            ResponseEntity<SpeedyCalculatePriceResponse> response = restTemplate.postForEntity(
                    speedyConfig.getBaseUrl() + "/calculator/price",
                    request,
                    SpeedyCalculatePriceResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            } else {
                log.error("Failed to calculate shipping price: {}", response.getStatusCode());
                throw new RuntimeException("Failed to calculate shipping price");
            }
        } catch (Exception e) {
            log.error("Error calculating shipping price: {}", e.getMessage());
            throw new RuntimeException("Failed to calculate shipping price", e);
        }
    }

    /**
     * Проверява дали има валиден токен и аутентикира се ако е необходимо
     */
    private void ensureAuthenticated() {
        if (authToken == null) {
            authenticate();
        }
    }

    /**
     * Създава headers с авторизация
     */
    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}