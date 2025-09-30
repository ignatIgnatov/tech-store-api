package com.techstore.controller;

import com.techstore.dto.request.FavoriteRequestDto;
import com.techstore.dto.response.FavoriteCountResponseDto;
import com.techstore.dto.response.UserFavoriteResponseDto;
import com.techstore.service.UserFavoriteService;
import com.techstore.util.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "User Favorites", description = "API for managing favorite products")
public class UserFavoriteController {

    private final UserFavoriteService userFavoriteService;
    private final SecurityHelper securityHelper;

    @GetMapping
//    @PreAuthorize("isAuthenticated()")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved favorites"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated user"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<Page<UserFavoriteResponseDto>> getUserFavorites(
            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(description = "Page size (1-100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,

            @Parameter(description = "Localization language (en/bg)", example = "bg")
            @RequestParam(defaultValue = "bg") @Pattern(regexp = "^(en|bg)$", message = "Language must be 'en' or 'bg'") String language) {

        Long userId = securityHelper.getCurrentUserId();
        log.info("Getting favorites for user: {}, page: {}, size: {}", userId, page, size);

        Page<UserFavoriteResponseDto> favorites = userFavoriteService.getUserFavorites(userId, page, size, language);
        return ResponseEntity.ok(favorites);
    }

    @PostMapping
//    @PreAuthorize("isAuthenticated()")
    @Operation(
            description = "Adds or removes products from favorites"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Favorite status successfully updated"),
            @ApiResponse(responseCode = "400", description = "Invalid data"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated user"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ResponseEntity<Boolean> toggleFavorite(
            @Valid @RequestBody FavoriteRequestDto request) {

        Long userId = securityHelper.getCurrentUserId();
        log.info("Toggling favorite status for product {} and user {}", request.getProductId(), userId);

        boolean wasInFavorites = userFavoriteService.isProductInFavorites(userId, request.getProductId());
        userFavoriteService.toggleFavorite(userId, request.getProductId());

        return ResponseEntity.ok(!wasInFavorites);
    }

//    @DeleteMapping("/product/{productId}")
//    @PreAuthorize("isAuthenticated()")
//    @ApiResponses({
//            @ApiResponse(responseCode = "200", description = "Product successfully removed"),
//            @ApiResponse(responseCode = "400", description = "Invalid product ID"),
//            @ApiResponse(responseCode = "401", description = "Unauthenticated user"),
//            @ApiResponse(responseCode = "403", description = "Access denied"),
//            @ApiResponse(responseCode = "404", description = "Product not found in favorites")
//    })
//    public ResponseEntity<Void> removeFromFavorites(
//            @Parameter(description = "ID of the product to remove", example = "1", required = true)
//            @PathVariable Long productId) {
//
//        Long userId = securityHelper.getCurrentUserId();
//        log.info("Removing product {} from favorites for user {}", productId, userId);
//
//        userFavoriteService.removeFromFavorites(userId, productId);
//        return ResponseEntity.ok().build();
//    }

//    @DeleteMapping("/{favoriteId}")
//    @PreAuthorize("isAuthenticated()")
//    @Operation(
//            summary = "Remove favorite by ID",
//            description = "Removes a favorite entry by its ID"
//    )
//    @ApiResponses({
//            @ApiResponse(responseCode = "200", description = "Favorite successfully removed"),
//            @ApiResponse(responseCode = "400", description = "Invalid favorite ID"),
//            @ApiResponse(responseCode = "401", description = "Unauthenticated user"),
//            @ApiResponse(responseCode = "403", description = "Access denied or entry does not belong to the user"),
//            @ApiResponse(responseCode = "404", description = "Favorite not found")
//    })
//    public ResponseEntity<Void> removeFromFavoritesById(
//            @Parameter(description = "ID of the favorite entry", example = "1", required = true)
//            @PathVariable Long favoriteId) {
//
//        Long userId = securityHelper.getCurrentUserId();
//        log.info("Removing favorite {} for user {}", favoriteId, userId);
//
//        userFavoriteService.removeFromFavoritesByFavoriteId(userId, favoriteId);
//        return ResponseEntity.ok().build();
//    }

    @DeleteMapping
//    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Clear all favorites",
            description = "Removes all products from the favorites list of the current user"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All favorites successfully cleared"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated user"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<Void> clearFavorites() {
        Long userId = securityHelper.getCurrentUserId();
        log.info("Clearing all favorites for user {}", userId);

        userFavoriteService.clearUserFavorites(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/count")
//    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Get number of favorite products",
            description = "Returns the current number of favorite products and limit information"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved count"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated user"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<FavoriteCountResponseDto> getFavoriteCount() {
        Long userId = securityHelper.getCurrentUserId();
        log.debug("Getting favorite count for user {}", userId);

        Long count = userFavoriteService.getFavoriteCount(userId);
        FavoriteCountResponseDto response = new FavoriteCountResponseDto(count, 1000); // MAX_FAVORITES_PER_USER
        return ResponseEntity.ok(response);
    }

    @GetMapping("/check/{productId}")
//    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Check if product is in favorites",
            description = "Checks whether the specified product is in the current user's favorites list"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Check successful"),
            @ApiResponse(responseCode = "400", description = "Invalid product ID"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated user"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<Boolean> isProductInFavorites(
            @Parameter(description = "ID of the product to check", example = "1", required = true)
            @PathVariable Long productId) {

        Long userId = securityHelper.getCurrentUserId();
        log.debug("Checking if product {} is in favorites for user {}", productId, userId);

        boolean isInFavorites = userFavoriteService.isProductInFavorites(userId, productId);
        return ResponseEntity.ok(isInFavorites);
    }

    @GetMapping("/limit-reached")
//    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Check if favorites limit is reached",
            description = "Checks whether the user has reached the maximum number of favorite products"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Check successful"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated user"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<Boolean> hasReachedFavoritesLimit() {
        Long userId = securityHelper.getCurrentUserId();
        log.debug("Checking favorites limit for user {}", userId);

        boolean limitReached = userFavoriteService.hasReachedFavoritesLimit(userId);
        return ResponseEntity.ok(limitReached);
    }
}
