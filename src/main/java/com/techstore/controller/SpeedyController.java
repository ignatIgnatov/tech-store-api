package com.techstore.controller;

import com.techstore.dto.speedy.SpeedyCalculatePriceResponse;
import com.techstore.dto.speedy.SpeedyOffice;
import com.techstore.dto.speedy.SpeedySite;
import com.techstore.service.SpeedyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/speedy")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Speedy Shipping", description = "API for Speedy courier service integration")
public class SpeedyController {

    private final SpeedyService speedyService;

    @Operation(
            summary = "Search for cities/sites by name",
            description = "Retrieve a list of Speedy sites (cities) based on search criteria. Used for address selection during checkout."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved sites list",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SpeedySite[].class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error"
            )
    })
    @GetMapping("/cities")
    public ResponseEntity<List<SpeedySite>> getCities(
            @Parameter(
                    description = "City name or partial name to search for",
                    example = "Sofia",
                    required = true
            )
            @RequestParam String name) {

        try {
            List<SpeedySite> sites = speedyService.getCitiesByName(name);
            return ResponseEntity.ok(sites);
        } catch (Exception e) {
            log.error("Error fetching sites: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(
            summary = "Get offices in a specific city",
            description = "Retrieve all Speedy offices available in a given city/site. Used for office delivery selection."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved offices list",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SpeedyOffice[].class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error"
            )
    })
    @GetMapping("/offices/{cityId}")
    public ResponseEntity<List<SpeedyOffice>> getOffices(
            @Parameter(
                    description = "Speedy city ID obtained from sites search",
                    example = "123456",
                    required = true
            )
            @PathVariable Long cityId) {

        try {
            List<SpeedyOffice> offices = speedyService.getOfficesByCityId(cityId);
            return ResponseEntity.ok(offices);
        } catch (Exception e) {
            log.error("Error fetching offices: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(
            summary = "Calculate shipping price",
            description = "Calculate the shipping cost based on destination, weight, and number of parcels. Used for displaying shipping costs during checkout."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully calculated shipping price",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SpeedyCalculatePriceResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - invalid parameters"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error"
            )
    })
    @GetMapping("/calculate-price")
    public ResponseEntity<?> calculatePrice(
            @Parameter(
                    description = "Destination city ID (Speedy site ID)",
                    example = "123456",
                    required = true
            )
            @RequestParam Long receiverSiteId,

            @Parameter(
                    description = "Total weight of the order in kilograms",
                    example = "2.5",
                    required = true
            )
            @RequestParam BigDecimal totalWeight,

            @Parameter(
                    description = "Number of parcels in the shipment",
                    example = "1"
            )
            @RequestParam(defaultValue = "1") Integer parcelCount) {

        try {
            var priceResponse = speedyService.calculateShippingPrice(receiverSiteId, totalWeight, parcelCount);
            return ResponseEntity.ok(priceResponse);
        } catch (Exception e) {
            log.error("Error calculating price: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}