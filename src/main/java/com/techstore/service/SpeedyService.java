package com.techstore.service;

import com.techstore.config.SpeedyConfig;
import com.techstore.dto.speedy.SpeedyCalculatePriceRequest;
import com.techstore.dto.speedy.SpeedyCalculatePriceResponse;
import com.techstore.dto.speedy.SpeedyOffice;
import com.techstore.dto.speedy.SpeedyOfficeResponse;
import com.techstore.dto.speedy.SpeedySite;
import com.techstore.dto.speedy.SpeedySiteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpeedyService {

    private final RestTemplate restTemplate;
    private final SpeedyConfig speedyConfig;

    // NOTE: Speedy API does NOT use token-based authentication
    // Username and password are sent with EVERY request

    /**
     * Взема населени места по име
     */
    public List<SpeedySite> getCitiesByName(String name) {
        try {
            // Create request body with authentication
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("userName", speedyConfig.getUserName());
            requestBody.put("password", speedyConfig.getPassword());
            requestBody.put("name", name);
            requestBody.put("countryId", 100); // България

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            String url = speedyConfig.getBaseUrl() + "location/site";

            log.info("Fetching cities from Speedy API with name: {}", name);

            // FIXED: Use SpeedySiteResponse wrapper instead of SpeedySite[]
            ResponseEntity<SpeedySiteResponse> response = restTemplate.postForEntity(
                    url,
                    request,
                    SpeedySiteResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                SpeedySiteResponse responseBody = response.getBody();

                if (responseBody.getSites() != null) {
                    log.info("Successfully fetched {} cities from Speedy API", responseBody.getSites().size());
                    return responseBody.getSites();
                }
            }

            log.warn("Received empty response from Speedy API");
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching sites from Speedy API: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Взема офиси в дадено населено място
     */
    public List<SpeedyOffice> getOfficesByCityId(Long siteId) {
        try {
            // Create request body with authentication
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("userName", speedyConfig.getUserName());
            requestBody.put("password", speedyConfig.getPassword());
            requestBody.put("siteId", siteId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            String url = speedyConfig.getBaseUrl() + "location/office";

            log.info("Fetching offices from Speedy API for siteId: {}", siteId);

            // FIXED: Use SpeedyOfficeResponse wrapper instead of SpeedyOffice[]
            ResponseEntity<SpeedyOfficeResponse> response = restTemplate.postForEntity(
                    url,
                    request,
                    SpeedyOfficeResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                SpeedyOfficeResponse responseBody = response.getBody();

                if (responseBody.getOffices() != null) {
                    log.info("Successfully fetched {} offices from Speedy API", responseBody.getOffices().size());
                    return responseBody.getOffices();
                }
            }

            log.warn("Received empty response from Speedy API");
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching offices from Speedy API: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Изчислява цена на доставка
     */
    public SpeedyCalculatePriceResponse calculateShippingPrice(Long receiverSiteId, BigDecimal totalWeight, Integer parcelCount) {
        try {
            SpeedyCalculatePriceRequest priceRequest = new SpeedyCalculatePriceRequest();

            // Add authentication to the request
            priceRequest.setUserName(speedyConfig.getUserName());
            priceRequest.setPassword(speedyConfig.getPassword());
            priceRequest.setLanguage("BG");

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
            SpeedyCalculatePriceRequest.Payment payment = new SpeedyCalculatePriceRequest.Payment();
            payment.setCourierServicePayer("SENDER");
            priceRequest.setPayment(payment);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<SpeedyCalculatePriceRequest> request = new HttpEntity<>(priceRequest, headers);

            String url = speedyConfig.getBaseUrl() + "calculate";

            log.info("Calculating shipping price for receiverSiteId: {}, weight: {}, parcels: {}",
                    receiverSiteId, totalWeight, parcelCount);

            ResponseEntity<SpeedyCalculatePriceResponse> response = restTemplate.postForEntity(
                    url,
                    request,
                    SpeedyCalculatePriceResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("Successfully calculated shipping price");
                return response.getBody();
            } else {
                log.error("Failed to calculate shipping price: {}", response.getStatusCode());
                throw new RuntimeException("Failed to calculate shipping price");
            }
        } catch (Exception e) {
            log.error("Error calculating shipping price: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to calculate shipping price", e);
        }
    }
}