package com.techstore.controller;

import com.techstore.dto.request.CartItemRequestDto;
import com.techstore.dto.response.CartItemResponseDto;
import com.techstore.dto.response.CartSummaryDto;
import com.techstore.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Cart Management", description = "APIs for managing shopping cart operations")
public class CartController {

    private final CartService cartService;

    @GetMapping("/{userId}/summary")
    @Operation(
            summary = "Get cart summary",
            description = "Retrieves the complete cart summary including all items, total quantity, and total price for a user"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cart summary retrieved successfully",
                    content = @Content(schema = @Schema(implementation = CartSummaryDto.class))),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CartSummaryDto> getCartSummary(
            @Parameter(description = "User ID", example = "1", required = true)
            @PathVariable Long userId,

            @Parameter(description = "Language code for product localization", example = "en")
            @RequestParam(defaultValue = "en") String language) {

        log.info("Getting cart summary for user: {}", userId);
        CartSummaryDto cartSummary = cartService.getCartSummary(userId, language);
        return ResponseEntity.ok(cartSummary);
    }

    @PostMapping("/{userId}/items")
    @Operation(
            summary = "Add item to cart",
            description = "Adds a product to the user's cart. If the product already exists, it updates the quantity."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Item added to cart successfully",
                    content = @Content(schema = @Schema(implementation = CartItemResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "404", description = "User or product not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CartItemResponseDto> addToCart(
            @Parameter(description = "User ID", example = "1", required = true)
            @PathVariable Long userId,

            @Parameter(description = "Cart item request details", required = true)
            @Valid @RequestBody CartItemRequestDto request,

            @Parameter(description = "Language code for product localization", example = "en")
            @RequestParam(defaultValue = "en") String language) {

        log.info("Adding item to cart for user: {}, product: {}", userId, request.getProductId());
        CartItemResponseDto cartItem = cartService.addToCart(userId, request, language);
        return ResponseEntity.ok(cartItem);
    }

    @PutMapping("/{userId}/items/{cartItemId}")
    @Operation(
            summary = "Update cart item quantity",
            description = "Updates the quantity of a specific item in the user's cart"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cart item updated successfully",
                    content = @Content(schema = @Schema(implementation = CartItemResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid quantity"),
            @ApiResponse(responseCode = "404", description = "Cart item or user not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CartItemResponseDto> updateCartItem(
            @Parameter(description = "User ID", example = "1", required = true)
            @PathVariable Long userId,

            @Parameter(description = "Cart item ID", example = "1", required = true)
            @PathVariable Long cartItemId,

            @Parameter(description = "New quantity for the cart item", example = "5", required = true)
            @RequestParam Integer quantity,

            @Parameter(description = "Language code for product localization", example = "en")
            @RequestParam(defaultValue = "en") String language) {

        log.info("Updating cart item: {} for user: {} with quantity: {}", cartItemId, userId, quantity);
        CartItemResponseDto updatedItem = cartService.updateCartItem(userId, cartItemId, quantity, language);
        return ResponseEntity.ok(updatedItem);
    }

    @DeleteMapping("/{userId}/items/{cartItemId}")
    @Operation(
            summary = "Remove item from cart",
            description = "Removes a specific item from the user's cart"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Item removed successfully"),
            @ApiResponse(responseCode = "404", description = "Cart item or user not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> removeFromCart(
            @Parameter(description = "User ID", example = "1", required = true)
            @PathVariable Long userId,

            @Parameter(description = "Cart item ID", example = "1", required = true)
            @PathVariable Long cartItemId) {

        log.info("Removing cart item: {} for user: {}", cartItemId, userId);
        cartService.removeFromCart(userId, cartItemId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}/clear")
    @Operation(
            summary = "Clear entire cart",
            description = "Removes all items from the user's cart"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cart cleared successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> clearCart(
            @Parameter(description = "User ID", example = "1", required = true)
            @PathVariable Long userId) {

        log.info("Clearing cart for user: {}", userId);
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }
}