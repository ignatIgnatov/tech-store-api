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
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "User Favorites", description = "API за управление на любими продукти")
public class UserFavoriteController {

    private final UserFavoriteService userFavoriteService;
    private final SecurityHelper securityHelper;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Вземи списък с любими продукти",
            description = "Връща странициран списък с любими продукти на текущия потребител"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Успешно вземане на любими"),
            @ApiResponse(responseCode = "400", description = "Невалидни параметри"),
            @ApiResponse(responseCode = "401", description = "Неавтентикиран потребител"),
            @ApiResponse(responseCode = "403", description = "Няма права за достъп")
    })
    public ResponseEntity<Page<UserFavoriteResponseDto>> getUserFavorites(
            @Parameter(description = "Номер на страница (0-based)", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(description = "Размер на страница (1-100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,

            @Parameter(description = "Език за локализация (en/bg)", example = "bg")
            @RequestParam(defaultValue = "bg") @Pattern(regexp = "^(en|bg)$", message = "Езикът трябва да е 'en' или 'bg'") String language) {

        Long userId = securityHelper.getCurrentUserId();
        log.info("Getting favorites for user: {}, page: {}, size: {}", userId, page, size);

        Page<UserFavoriteResponseDto> favorites = userFavoriteService.getUserFavorites(userId, page, size, language);
        return ResponseEntity.ok(favorites);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Добави продукт в любими",
            description = "Добавя указан продукт в списъка с любими на текущия потребител"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Продуктът е добавен успешно"),
            @ApiResponse(responseCode = "400", description = "Невалидни данни"),
            @ApiResponse(responseCode = "401", description = "Неавтентикиран потребител"),
            @ApiResponse(responseCode = "403", description = "Няма права за достъп"),
            @ApiResponse(responseCode = "404", description = "Продуктът не е намерен")
    })
    public ResponseEntity<Void> addToFavorites(
            @Valid @RequestBody FavoriteRequestDto request) {

        Long userId = securityHelper.getCurrentUserId();
        log.info("Adding product {} to favorites for user {}", request.getProductId(), userId);

        userFavoriteService.addToFavorites(userId, request.getProductId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/product/{productId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Премахни продукт от любими",
            description = "Премахва продукт от списъка с любими на текущия потребител по ID на продукта"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Продуктът е премахнат успешно"),
            @ApiResponse(responseCode = "400", description = "Невалидно ID на продукт"),
            @ApiResponse(responseCode = "401", description = "Неавтентикиран потребител"),
            @ApiResponse(responseCode = "403", description = "Няма права за достъп"),
            @ApiResponse(responseCode = "404", description = "Продуктът не е намерен в любими")
    })
    public ResponseEntity<Void> removeFromFavorites(
            @Parameter(description = "ID на продукта за премахване", example = "1", required = true)
            @PathVariable Long productId) {

        Long userId = securityHelper.getCurrentUserId();
        log.info("Removing product {} from favorites for user {}", productId, userId);

        userFavoriteService.removeFromFavorites(userId, productId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{favoriteId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Премахни любим по ID",
            description = "Премахва запис от любими по неговото ID"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Записът е премахнат успешно"),
            @ApiResponse(responseCode = "400", description = "Невалидно ID на любим"),
            @ApiResponse(responseCode = "401", description = "Неавтентикиран потребител"),
            @ApiResponse(responseCode = "403", description = "Няма права за достъп или записът не принадлежи на потребителя"),
            @ApiResponse(responseCode = "404", description = "Записът не е намерен")
    })
    public ResponseEntity<Void> removeFromFavoritesById(
            @Parameter(description = "ID на записа за любим", example = "1", required = true)
            @PathVariable Long favoriteId) {

        Long userId = securityHelper.getCurrentUserId();
        log.info("Removing favorite {} for user {}", favoriteId, userId);

        userFavoriteService.removeFromFavoritesByFavoriteId(userId, favoriteId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Изчисти всички любими",
            description = "Премахва всички продукти от списъка с любими на текущия потребител"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Всички любими са изчистени успешно"),
            @ApiResponse(responseCode = "401", description = "Неавтентикиран потребител"),
            @ApiResponse(responseCode = "403", description = "Няма права за достъп")
    })
    public ResponseEntity<Void> clearFavorites() {
        Long userId = securityHelper.getCurrentUserId();
        log.info("Clearing all favorites for user {}", userId);

        userFavoriteService.clearUserFavorites(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/count")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Вземи брой любими продукти",
            description = "Връща текущия брой любими продукти и информация за лимита"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Успешно вземане на броя"),
            @ApiResponse(responseCode = "401", description = "Неавтентикиран потребител"),
            @ApiResponse(responseCode = "403", description = "Няма права за достъп")
    })
    public ResponseEntity<FavoriteCountResponseDto> getFavoriteCount() {
        Long userId = securityHelper.getCurrentUserId();
        log.debug("Getting favorite count for user {}", userId);

        Long count = userFavoriteService.getFavoriteCount(userId);
        FavoriteCountResponseDto response = new FavoriteCountResponseDto(count, 1000); // MAX_FAVORITES_PER_USER
        return ResponseEntity.ok(response);
    }

    @GetMapping("/check/{productId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Провери дали продукт е в любими",
            description = "Проверява дали указаният продукт е в списъка с любими на текущия потребител"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Успешна проверка"),
            @ApiResponse(responseCode = "400", description = "Невалидно ID на продукт"),
            @ApiResponse(responseCode = "401", description = "Неавтентикиран потребител"),
            @ApiResponse(responseCode = "403", description = "Няма права за достъп")
    })
    public ResponseEntity<Boolean> isProductInFavorites(
            @Parameter(description = "ID на продукта за проверка", example = "1", required = true)
            @PathVariable Long productId) {

        Long userId = securityHelper.getCurrentUserId();
        log.debug("Checking if product {} is in favorites for user {}", productId, userId);

        boolean isInFavorites = userFavoriteService.isProductInFavorites(userId, productId);
        return ResponseEntity.ok(isInFavorites);
    }

    @PostMapping("/toggle")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Превключи статуса на любим",
            description = "Добавя или премахва продукт от любими в зависимост от текущия му статус"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Статусът е променен успешно"),
            @ApiResponse(responseCode = "400", description = "Невалидни данни"),
            @ApiResponse(responseCode = "401", description = "Неавтентикиран потребител"),
            @ApiResponse(responseCode = "403", description = "Няма права за достъп"),
            @ApiResponse(responseCode = "404", description = "Продуктът не е намерен")
    })
    public ResponseEntity<Boolean> toggleFavorite(
            @Valid @RequestBody FavoriteRequestDto request) {

        Long userId = securityHelper.getCurrentUserId();
        log.info("Toggling favorite status for product {} and user {}", request.getProductId(), userId);

        // Вземете текущия статус преди превключване
        boolean wasInFavorites = userFavoriteService.isProductInFavorites(userId, request.getProductId());

        // Превключете статуса
        userFavoriteService.toggleFavorite(userId, request.getProductId());

        // Върнете новия статус (обратен на предишния)
        return ResponseEntity.ok(!wasInFavorites);
    }

    @GetMapping("/limit-reached")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Провери дали е достигнат лимита за любими",
            description = "Проверява дали потребителят е достигнал максималния брой любими продукти"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Успешна проверка"),
            @ApiResponse(responseCode = "401", description = "Неавтентикиран потребител"),
            @ApiResponse(responseCode = "403", description = "Няма права за достъп")
    })
    public ResponseEntity<Boolean> hasReachedFavoritesLimit() {
        Long userId = securityHelper.getCurrentUserId();
        log.debug("Checking favorites limit for user {}", userId);

        boolean limitReached = userFavoriteService.hasReachedFavoritesLimit(userId);
        return ResponseEntity.ok(limitReached);
    }
}