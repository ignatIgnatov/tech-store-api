package com.techstore.service;

import com.techstore.dto.response.ProductSummaryDto;
import com.techstore.dto.response.UserFavoriteResponseDto;
import com.techstore.entity.Product;
import com.techstore.entity.User;
import com.techstore.entity.UserFavorite;
import com.techstore.exception.BusinessLogicException;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.exception.ValidationException;
import com.techstore.mapper.ParameterMapper;
import com.techstore.repository.ProductRepository;
import com.techstore.repository.UserFavoriteRepository;
import com.techstore.repository.UserRepository;
import com.techstore.util.ExceptionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserFavoriteService {

    private final UserFavoriteRepository userFavoriteRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ParameterMapper parameterMapper;

    // Maximum favorites per user to prevent abuse
    private static final int MAX_FAVORITES_PER_USER = 1000;

    @Transactional(readOnly = true)
    public Page<UserFavoriteResponseDto> getUserFavorites(Long userId, int page, int size, String language) {
        log.debug("Fetching favorites for user: {} - Page: {}, Size: {}", userId, page, size);

        String context = ExceptionHelper.createErrorContext(
                "getUserFavorites", "UserFavorite", null, "userId: " + userId);

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            // Validate inputs
            validateUserId(userId);
            validatePaginationParameters(page, size);
            validateLanguage(language);

            // Verify user exists
            findUserByIdOrThrow(userId);

            PageRequest pageRequest = PageRequest.of(page, size);
            Page<UserFavorite> favorites = userFavoriteRepository.findByUserIdWithProducts(userId, pageRequest);

            return favorites.map(favorite -> convertToResponseDto(favorite, language));

        }, context);
    }

    public void addToFavorites(Long userId, Long productId) {
        log.info("Adding product {} to favorites for user {}", productId, userId);

        String context = ExceptionHelper.createErrorContext(
                "addToFavorites", "UserFavorite", null,
                String.format("userId: %d, productId: %d", userId, productId));

        ExceptionHelper.wrapDatabaseOperation(() -> {
            // Validate inputs
            validateUserId(userId);
            validateProductId(productId);

            // Find and validate entities
            User user = findUserByIdOrThrow(userId);
            Product product = findProductByIdOrThrow(productId);

            // Business validations
            validateUserForFavorites(user);
            validateProductForFavorites(product);

            // Check if already in favorites
            if (userFavoriteRepository.existsByUserIdAndProductId(userId, productId)) {
                log.debug("Product {} is already in favorites for user {}", productId, userId);
                return null; // Silently ignore - this is not an error condition
            }

            // Check favorites limit
            validateFavoritesLimit(userId);

            // Add to favorites
            UserFavorite favorite = new UserFavorite();
            favorite.setUser(user);
            favorite.setProduct(product);

            userFavoriteRepository.save(favorite);

            log.info("Product {} successfully added to favorites for user {}", productId, userId);
            return null;

        }, context);
    }

    public void removeFromFavorites(Long userId, Long productId) {
        log.info("Removing product {} from favorites for user {}", productId, userId);

        String context = ExceptionHelper.createErrorContext(
                "removeFromFavorites", "UserFavorite", null,
                String.format("userId: %d, productId: %d", userId, productId));

        ExceptionHelper.wrapDatabaseOperation(() -> {
            // Validate inputs
            validateUserId(userId);
            validateProductId(productId);

            // Verify user exists
            findUserByIdOrThrow(userId);

            // Check if favorite exists
            if (!userFavoriteRepository.existsByUserIdAndProductId(userId, productId)) {
                log.warn("Attempt to remove non-existent favorite - User: {}, Product: {}", userId, productId);
                throw new ResourceNotFoundException(
                        String.format("Product %d is not in favorites for user %d", productId, userId));
            }

            // Remove from favorites
            userFavoriteRepository.deleteByUserIdAndProductId(userId, productId);

            log.info("Product {} successfully removed from favorites for user {}", productId, userId);
            return null;

        }, context);
    }

//    public void removeFromFavoritesByFavoriteId(Long userId, Long favoriteId) {
//        log.info("Removing favorite {} for user {}", favoriteId, userId);
//
//        String context = ExceptionHelper.createErrorContext(
//                "removeFromFavoritesByFavoriteId", "UserFavorite", favoriteId, "userId: " + userId);
//
//        ExceptionHelper.wrapDatabaseOperation(() -> {
//            // Validate inputs
//            validateUserId(userId);
//            validateFavoriteId(favoriteId);
//
//            // Find favorite
//            UserFavorite favorite = findFavoriteByIdOrThrow(favoriteId);
//
//            // Verify ownership
//            if (!favorite.getUser().getId().equals(userId)) {
//                log.warn("User {} attempted to remove favorite {} belonging to user {}",
//                        userId, favoriteId, favorite.getUser().getId());
//                throw new BusinessLogicException("You can only remove your own favorites");
//            }
//
//            // Remove favorite
//            userFavoriteRepository.delete(favorite);
//
//            log.info("Favorite {} successfully removed for user {}", favoriteId, userId);
//            return null;
//
//        }, context);
//    }

    public void clearUserFavorites(Long userId) {
        log.info("Clearing all favorites for user {}", userId);

        String context = ExceptionHelper.createErrorContext("clearUserFavorites", "UserFavorite", null, "userId: " + userId);

        ExceptionHelper.wrapDatabaseOperation(() -> {
            // Validate input
            validateUserId(userId);

            // Verify user exists
            findUserByIdOrThrow(userId);

            // Get count before deletion for logging
            Long favoriteCount = userFavoriteRepository.countByUserId(userId);

            if (favoriteCount == 0) {
                log.debug("No favorites to clear for user {}", userId);
                return null;
            }

            // Clear all favorites - this will cascade delete properly
            userFavoriteRepository.deleteByUserId(userId);

            log.info("Successfully cleared {} favorites for user {}", favoriteCount, userId);
            return null;

        }, context);
    }

    @Transactional(readOnly = true)
    public boolean isProductInFavorites(Long userId, Long productId) {
        log.debug("Checking if product {} is in favorites for user {}", productId, userId);

        // Validate inputs
        validateUserId(userId);
        validateProductId(productId);

        return ExceptionHelper.wrapDatabaseOperation(() ->
                        userFavoriteRepository.existsByUserIdAndProductId(userId, productId),
                "check product in favorites"
        );
    }

    @Transactional(readOnly = true)
    public Long getFavoriteCount(Long userId) {
        log.debug("Getting favorite count for user {}", userId);

        validateUserId(userId);

        return ExceptionHelper.wrapDatabaseOperation(() ->
                        userFavoriteRepository.countByUserId(userId),
                "get favorite count"
        );
    }

    @Transactional(readOnly = true)
    public boolean hasReachedFavoritesLimit(Long userId) {
        validateUserId(userId);

        Long count = getFavoriteCount(userId);
        return count >= MAX_FAVORITES_PER_USER;
    }

    public void toggleFavorite(Long userId, Long productId) {
        log.info("Toggling favorite status for product {} and user {}", productId, userId);

        if (isProductInFavorites(userId, productId)) {
            removeFromFavorites(userId, productId);
        } else {
            addToFavorites(userId, productId);
        }
    }

    // ========== PRIVATE VALIDATION METHODS ==========

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ValidationException("User ID must be a positive number");
        }
    }

    private void validateProductId(Long productId) {
        if (productId == null || productId <= 0) {
            throw new ValidationException("Product ID must be a positive number");
        }
    }

    private void validateFavoriteId(Long favoriteId) {
        if (favoriteId == null || favoriteId <= 0) {
            throw new ValidationException("Favorite ID must be a positive number");
        }
    }

    private void validatePaginationParameters(int page, int size) {
        if (page < 0) {
            throw new ValidationException("Page number cannot be negative");
        }

        if (size <= 0) {
            throw new ValidationException("Page size must be positive");
        }

        if (size > 100) {
            throw new ValidationException("Page size cannot exceed 100");
        }
    }

    private void validateLanguage(String language) {
        if (!StringUtils.hasText(language)) {
            throw new ValidationException("Language is required");
        }

        if (!language.matches("^(en|bg)$")) {
            throw new ValidationException("Language must be 'en' or 'bg'");
        }
    }

    private void validateUserForFavorites(User user) {
        if (!user.getActive()) {
            throw new BusinessLogicException("Cannot manage favorites for inactive user account");
        }

        if (!user.getEmailVerified()) {
            throw new BusinessLogicException("Email must be verified to manage favorites");
        }
    }

    private void validateProductForFavorites(Product product) {
        if (!product.getActive()) {
            throw new BusinessLogicException("Cannot add inactive products to favorites");
        }

        if (!product.getShow()) {
            throw new BusinessLogicException("Cannot add hidden products to favorites");
        }

        // Optional: Check if product is available
        if (product.getStatus() != null && product.getStatus().getCode() == 0) {
            log.warn("User attempting to add unavailable product {} to favorites", product.getId());
            // Don't throw error - user might want to favorite for when it becomes available
        }
    }

    private void validateFavoritesLimit(Long userId) {
        Long currentCount = userFavoriteRepository.countByUserId(userId);

        if (currentCount >= MAX_FAVORITES_PER_USER) {
            throw new BusinessLogicException(
                    String.format("Cannot add more favorites. Maximum limit of %d favorites reached.",
                            MAX_FAVORITES_PER_USER));
        }
    }

    // ========== PRIVATE HELPER METHODS ==========

    private User findUserByIdOrThrow(Long userId) {
        return ExceptionHelper.findOrThrow(
                userRepository.findById(userId).orElse(null),
                "User",
                userId
        );
    }

    private Product findProductByIdOrThrow(Long productId) {
        return ExceptionHelper.findOrThrow(
                productRepository.findById(productId).orElse(null),
                "Product",
                productId
        );
    }

    public UserFavoriteResponseDto convertToResponseDto(UserFavorite favorite, String language) {
        UserFavoriteResponseDto dto = new UserFavoriteResponseDto();
        dto.setId(favorite.getId());
        dto.setProduct(convertProductToResponseDTO(favorite.getProduct(), language));
        dto.setCreatedAt(favorite.getCreatedAt());
        return dto;
    }

    private ProductSummaryDto convertProductToResponseDTO(Product product, String lang) {
        ProductSummaryDto dto = new ProductSummaryDto();
        dto.setId(product.getId());
        dto.setNameEn(product.getNameEn());
        dto.setNameBg(product.getNameBg());
        dto.setFinalPrice(product.getFinalPrice());

        if (product.getPrimaryImageUrl() != null) {
            dto.setPrimaryImageUrl("/api/images/product/" + product.getId() + "/primary");
        }
        return dto;
    }
}