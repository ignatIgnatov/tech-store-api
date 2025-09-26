package com.techstore.service;

import com.techstore.dto.request.CartItemRequestDto;
import com.techstore.dto.response.CartItemResponseDto;
import com.techstore.dto.response.CartSummaryDto;
import com.techstore.entity.CartItem;
import com.techstore.entity.Product;
import com.techstore.entity.User;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.mapper.ProductMapper;
import com.techstore.repository.CartItemRepository;
import com.techstore.repository.ProductRepository;
import com.techstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public CartSummaryDto getCartSummary(Long userId, String language) {
        List<CartItem> cartItems = cartItemRepository.findByUserIdOrderByCreatedAtAsc(userId);

        List<CartItemResponseDto> itemDtos = cartItems.stream()
                .map(cartItem -> {
                    CartItemResponseDto dto = mapToCartItemResponse(cartItem, cartItem.getProduct(), language);
                    return dto;
                })
                .toList();

        CartSummaryDto summary = new CartSummaryDto();
        summary.setItems(itemDtos);
        summary.setTotalItems(cartItems.stream().mapToInt(CartItem::getQuantity).sum());
        summary.setTotalPrice(cartItemRepository.calculateTotalPriceByUserId(userId));

        return summary;
    }

    @Transactional
    public CartItemResponseDto addToCart(Long userId, CartItemRequestDto request, String language) {
        log.info("Adding product {} to cart for user {}", request.getProductId(), userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.getProductId()));

        Optional<CartItem> existingItem = cartItemRepository.findByUserIdAndProductId(userId, request.getProductId());

        CartItem cartItem;
        if (existingItem.isPresent()) {
            cartItem = existingItem.get();
            cartItem.setQuantity(cartItem.getQuantity() + request.getQuantity());
        } else {
            cartItem = new CartItem();
            cartItem.setUser(user);
            cartItem.setProduct(product);
            cartItem.setQuantity(request.getQuantity());
        }

        cartItem = cartItemRepository.save(cartItem);

        CartItemResponseDto dto = mapToCartItemResponse(cartItem, product, language);

        return dto;
    }

    @Transactional
    public CartItemResponseDto updateCartItem(Long userId, Long cartItemId, Integer quantity, String language) {
        log.info("Updating cart item {} for user {} with quantity {}", cartItemId, userId, quantity);

        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with id: " + cartItemId));

        if (!cartItem.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Cart item not found for this user");
        }

        cartItem.setQuantity(quantity);
        cartItem = cartItemRepository.save(cartItem);

        return mapToCartItemResponse(cartItem, cartItem.getProduct(), language);
    }

    @Transactional
    public void removeFromCart(Long userId, Long cartItemId) {
        log.info("Removing cart item {} for user {}", cartItemId, userId);

        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found with id: " + cartItemId));

        if (!cartItem.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Cart item not found for this user");
        }

        cartItemRepository.delete(cartItem);
    }

    @Transactional
    public void clearCart(Long userId) {
        log.info("Clearing cart for user {}", userId);
        cartItemRepository.deleteByUserId(userId);
    }

    public CartItemResponseDto mapToCartItemResponse(CartItem cartItem, Product cartItem1, String language) {
        CartItemResponseDto dto = new CartItemResponseDto();
        dto.setId(cartItem.getId());
        dto.setProduct(productMapper.toSummaryDto(cartItem1, language));
        dto.setQuantity(cartItem.getQuantity());
        dto.setItemTotal(cartItem1.getFinalPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        dto.setCreatedAt(cartItem.getCreatedAt());
        dto.setUpdatedAt(cartItem.getUpdatedAt());
        return dto;
    }
}