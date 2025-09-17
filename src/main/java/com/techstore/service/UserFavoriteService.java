package com.techstore.service;

import com.techstore.dto.response.UserFavoriteResponseDto;
import com.techstore.entity.Product;
import com.techstore.entity.User;
import com.techstore.entity.UserFavorite;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.mapper.ProductMapper;
import com.techstore.repository.ProductRepository;
import com.techstore.repository.UserFavoriteRepository;
import com.techstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserFavoriteService {

    private final UserFavoriteRepository userFavoriteRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public Page<UserFavoriteResponseDto> getUserFavorites(Long userId, int page, int size, String language) {
        PageRequest pageRequest = PageRequest.of(page, size);

        Page<UserFavorite> favorites = userFavoriteRepository.findByUserIdWithProducts(userId, pageRequest);

        return favorites.map(favorite -> {
            UserFavoriteResponseDto dto = new UserFavoriteResponseDto();
            dto.setId(favorite.getId());
            dto.setProduct(productMapper.toSummaryDto(favorite.getProduct(), language));
            dto.setCreatedAt(favorite.getCreatedAt());
            return dto;
        });
    }

    @Transactional
    public void addToFavorites(Long userId, Long productId) {
        log.info("Adding product {} to favorites for user {}", productId, userId);

        if (userFavoriteRepository.existsByUserIdAndProductId(userId, productId)) {
            return; // Already in favorites
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        UserFavorite favorite = new UserFavorite();
        favorite.setUser(user);
        favorite.setProduct(product);

        userFavoriteRepository.save(favorite);
    }

    @Transactional
    public void removeFromFavorites(Long userId, Long productId) {
        log.info("Removing product {} from favorites for user {}", productId, userId);

        userFavoriteRepository.deleteByUserIdAndProductId(userId, productId);
    }

    public boolean isProductInFavorites(Long userId, Long productId) {
        return userFavoriteRepository.existsByUserIdAndProductId(userId, productId);
    }

    public Long getFavoriteCount(Long userId) {
        return userFavoriteRepository.countByUserId(userId);
    }
}