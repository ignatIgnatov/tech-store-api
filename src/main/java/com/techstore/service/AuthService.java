package com.techstore.service;

import com.techstore.dto.UserResponseDTO;
import com.techstore.dto.request.CartItemRequestDto;
import com.techstore.dto.request.LoginRequestDTO;
import com.techstore.dto.request.UserRequestDTO;
import com.techstore.dto.response.LoginResponseDTO;
import com.techstore.entity.CartItem;
import com.techstore.entity.Product;
import com.techstore.entity.User;
import com.techstore.entity.UserFavorite;
import com.techstore.exception.AccountNotActivatedException;
import com.techstore.exception.BusinessLogicException;
import com.techstore.exception.DuplicateResourceException;
import com.techstore.exception.InvalidCredentialsException;
import com.techstore.exception.InvalidTokenException;
import com.techstore.exception.ValidationException;
import com.techstore.repository.CartItemRepository;
import com.techstore.repository.UserRepository;
import com.techstore.util.ExceptionHelper;
import com.techstore.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final ProductService productService;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"
    );

    public LoginResponseDTO login(LoginRequestDTO loginRequest) {
        log.info("Login attempt for user: {}", loginRequest.getUsernameOrEmail());

        String context = ExceptionHelper.createErrorContext(
                "login", "User", null, loginRequest.getUsernameOrEmail());

        return ExceptionHelper.wrapBusinessOperation(() -> {
            // Validate login request
            validateLoginRequest(loginRequest);

            try {
                // Authenticate user
                Authentication authentication = authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                loginRequest.getUsernameOrEmail().trim(),
                                loginRequest.getPassword()
                        )
                );

                User user = (User) authentication.getPrincipal();

                // Perform post-authentication validations
                validateUserForLogin(user);

                // Generate JWT token
                String token = jwtUtil.generateToken(user);

                // Update last login timestamp
                updateLastLogin(user);

                log.info("User {} logged in successfully", user.getUsername());

                return LoginResponseDTO.builder()
                        .token(token)
                        .type("Bearer")
                        .user(convertToUserResponseDTO(user))
                        .build();

            } catch (BadCredentialsException e) {
                log.warn("Invalid credentials for user: {}", loginRequest.getUsernameOrEmail());
                throw new InvalidCredentialsException("Invalid username/email or password");

            } catch (DisabledException e) {
                log.warn("Attempt to login with disabled account: {}", loginRequest.getUsernameOrEmail());
                throw new AccountNotActivatedException("Account is disabled. Please contact support.");

            } catch (LockedException e) {
                log.warn("Attempt to login with locked account: {}", loginRequest.getUsernameOrEmail());
                throw new AccountNotActivatedException("Account is locked. Please contact support.");

            } catch (AuthenticationException e) {
                log.error("Authentication failed for user {}: {}", loginRequest.getUsernameOrEmail(), e.getMessage());
                throw new InvalidCredentialsException("Authentication failed: " + e.getMessage());
            }
        }, context);
    }

    public UserResponseDTO register(UserRequestDTO registerRequest) {
        log.info("Registration attempt for user: {}", registerRequest.getUsername());

        String context = ExceptionHelper.createErrorContext(
                "register", "User", null, registerRequest.getUsername());

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            // Comprehensive validation
            validateRegistrationRequest(registerRequest);

            // Check for existing users
            checkForExistingUser(registerRequest);

            // Create new user
            User user = createUserFromRegistration(registerRequest);
            user = userRepository.save(user);

            // Send verification email (implementation would depend on email service)
            sendVerificationEmailSafely(user);

            log.info("User {} registered successfully", user.getUsername());
            return convertToUserResponseDTO(user);

        }, context);
    }

    public void logout(String token) {
        log.info("User logout request received");

        try {
            // Extract username from token for logging
            String username = null;
            if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
                try {
                    username = jwtUtil.extractUsername(token.substring(7));
                } catch (Exception e) {
                    log.debug("Could not extract username from token for logout logging");
                }
            }

            // In a production system, you would add the token to a blacklist
            // For now, we just log the logout
            log.info("User {} logged out successfully", username != null ? username : "unknown");

        } catch (Exception e) {
            log.error("Error during logout: {}", e.getMessage());
            // Don't throw exception for logout - it should always succeed
        }
    }

    public LoginResponseDTO refreshToken(String token) {
        log.info("Token refresh request received");

        String context = ExceptionHelper.createErrorContext("refreshToken", "Token", null, null);

        return ExceptionHelper.wrapBusinessOperation(() -> {
            // Validate token format
            if (!StringUtils.hasText(token)) {
                throw new InvalidTokenException("Token is required");
            }

            // Remove "Bearer " prefix if present
            String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;

            try {
                String username = jwtUtil.extractUsername(cleanToken);
                User user = findUserByUsernameOrThrow(username);

                // Validate user is still active and email verified
                validateUserForTokenRefresh(user);

                if (jwtUtil.isTokenValid(cleanToken, user)) {
                    String newToken = jwtUtil.generateToken(user);

                    log.info("Token refreshed successfully for user: {}", username);

                    return LoginResponseDTO.builder()
                            .token(newToken)
                            .type("Bearer")
                            .user(convertToUserResponseDTO(user))
                            .build();
                } else {
                    throw new InvalidTokenException("Token is invalid or expired");
                }

            } catch (Exception e) {
                log.error("Token refresh failed: {}", e.getMessage());
                if (e instanceof RuntimeException) {
                    throw e;
                }
                throw new InvalidTokenException("Token refresh failed: " + e.getMessage());
            }
        }, context);
    }

    public void forgotPassword(String email) {
        log.info("Password reset request for email: {}", email);

        String context = ExceptionHelper.createErrorContext("forgotPassword", "User", null, email);

        ExceptionHelper.wrapBusinessOperation(() -> {
            // Validate email format
            validateEmail(email);

            User user = findUserByEmailOrThrow(email);

            // Generate password reset token
            String resetToken = jwtUtil.generatePasswordResetToken(user);

            // Send password reset email (implementation would depend on email service)
            sendPasswordResetEmailSafely(user, resetToken);

            log.info("Password reset email sent to: {}", email);
            return null;
        }, context);
    }

    public void resetPassword(String token, String newPassword) {
        log.info("Password reset attempt with token");

        String context = ExceptionHelper.createErrorContext("resetPassword", "User", null, null);

        ExceptionHelper.wrapDatabaseOperation(() -> {
            // Validate inputs
            if (!StringUtils.hasText(token)) {
                throw new InvalidTokenException("Reset token is required");
            }

            if (!StringUtils.hasText(newPassword)) {
                throw new ValidationException("New password is required");
            }

            validatePassword(newPassword);

            try {
                String username = jwtUtil.extractUsername(token);
                User user = findUserByUsernameOrThrow(username);

                if (jwtUtil.isPasswordResetTokenValid(token)) {
                    user.setPassword(passwordEncoder.encode(newPassword));
                    userRepository.save(user);

                    log.info("Password reset successfully for user: {}", username);
                } else {
                    throw new InvalidTokenException("Invalid or expired reset token");
                }

            } catch (Exception e) {
                log.error("Password reset failed: {}", e.getMessage());
                if (e instanceof RuntimeException) {
                    throw e;
                }
                throw new InvalidTokenException("Password reset failed: " + e.getMessage());
            }

            return null;
        }, context);
    }

    public void verifyEmail(String token) {
        log.info("Email verification attempt with token");

        String context = ExceptionHelper.createErrorContext("verifyEmail", "User", null, null);

        ExceptionHelper.wrapDatabaseOperation(() -> {
            if (!StringUtils.hasText(token)) {
                throw new InvalidTokenException("Verification token is required");
            }

            try {
                String username = jwtUtil.extractUsername(token);
                User user = findUserByUsernameOrThrow(username);

                if (user.getEmailVerified()) {
                    throw new BusinessLogicException("Email is already verified");
                }

                if (jwtUtil.isEmailVerificationTokenValid(token)) {
                    user.setEmailVerified(true);
                    userRepository.save(user);

                    log.info("Email verified successfully for user: {}", username);
                } else {
                    throw new InvalidTokenException("Invalid or expired verification token");
                }

            } catch (Exception e) {
                log.error("Email verification failed: {}", e.getMessage());
                if (e instanceof RuntimeException) {
                    throw e;
                }
                throw new InvalidTokenException("Email verification failed: " + e.getMessage());
            }

            return null;
        }, context);
    }

    public void resendVerificationEmail(String email) {
        log.info("Resend verification email request for: {}", email);

        String context = ExceptionHelper.createErrorContext("resendVerificationEmail", "User", null, email);

        ExceptionHelper.wrapBusinessOperation(() -> {
            validateEmail(email);

            User user = findUserByEmailOrThrow(email);

            if (user.getEmailVerified()) {
                throw new BusinessLogicException("Email is already verified");
            }

            // Send verification email
            sendVerificationEmailSafely(user);

            log.info("Verification email resent to: {}", email);
            return null;
        }, context);
    }

    // ========== PRIVATE VALIDATION METHODS ==========

    private void validateLoginRequest(LoginRequestDTO loginRequest) {
        if (loginRequest == null) {
            throw new ValidationException("Login request cannot be null");
        }

        if (!StringUtils.hasText(loginRequest.getUsernameOrEmail())) {
            throw new ValidationException("Username or email is required");
        }

        if (!StringUtils.hasText(loginRequest.getPassword())) {
            throw new ValidationException("Password is required");
        }

        if (loginRequest.getUsernameOrEmail().trim().length() > 200) {
            throw new ValidationException("Username or email is too long");
        }
    }

    private void validateRegistrationRequest(UserRequestDTO registerRequest) {
        if (registerRequest == null) {
            throw new ValidationException("Registration request cannot be null");
        }

        if (!StringUtils.hasText(registerRequest.getUsername())) {
            throw new ValidationException("Username is required");
        }

        if (!StringUtils.hasText(registerRequest.getEmail())) {
            throw new ValidationException("Email is required");
        }

        if (!StringUtils.hasText(registerRequest.getPassword())) {
            throw new ValidationException("Password is required");
        }

        // Validate username format
        String username = registerRequest.getUsername().trim();
        if (username.length() < 3 || username.length() > 100) {
            throw new ValidationException("Username must be between 3 and 100 characters");
        }

        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            throw new ValidationException("Username can only contain letters, numbers, and underscores");
        }

        // Validate email format
        validateEmail(registerRequest.getEmail());

        // Validate password strength
        validatePassword(registerRequest.getPassword());
    }

    private void validateEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new ValidationException("Email is required");
        }

        String trimmedEmail = email.trim().toLowerCase();

        if (trimmedEmail.length() > 200) {
            throw new ValidationException("Email cannot exceed 200 characters");
        }

        if (!EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
            throw new ValidationException("Invalid email format");
        }
    }

    private void validatePassword(String password) {
        if (!StringUtils.hasText(password)) {
            throw new ValidationException("Password is required");
        }

        if (password.length() < 8) {
            throw new ValidationException("Password must be at least 8 characters long");
        }

        if (password.length() > 100) {
            throw new ValidationException("Password cannot exceed 100 characters");
        }

        // Check password complexity
        if (!password.matches(".*[a-z].*")) {
            throw new ValidationException("Password must contain at least one lowercase letter");
        }

        if (!password.matches(".*[A-Z].*")) {
            throw new ValidationException("Password must contain at least one uppercase letter");
        }

        if (!password.matches(".*[0-9].*")) {
            throw new ValidationException("Password must contain at least one number");
        }

        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
            throw new ValidationException("Password must contain at least one special character");
        }
    }

    private void validateUserForLogin(User user) {
        if (!user.getActive()) {
            throw new AccountNotActivatedException("Account is deactivated. Please contact support.");
        }

        if (!user.getEmailVerified()) {
            throw new AccountNotActivatedException("Email not verified. Please verify your email first.");
        }
    }

    private void validateUserForTokenRefresh(User user) {
        if (!user.getActive()) {
            throw new AccountNotActivatedException("Account is deactivated");
        }

        if (!user.getEmailVerified()) {
            throw new AccountNotActivatedException("Email not verified");
        }
    }

    // ========== PRIVATE HELPER METHODS ==========

    private User findUserByUsernameOrThrow(String username) {
        return ExceptionHelper.findOrThrow(
                userRepository.findByUsername(username).orElse(null),
                "User",
                "username: " + username
        );
    }

    private User findUserByEmailOrThrow(String email) {
        return ExceptionHelper.findOrThrow(
                userRepository.findByEmail(email.trim().toLowerCase()).orElse(null),
                "User",
                "email: " + email
        );
    }

    private void checkForExistingUser(UserRequestDTO registerRequest) {
        if (userRepository.existsByUsername(registerRequest.getUsername().trim())) {
            throw new DuplicateResourceException("Username already exists");
        }

        if (userRepository.existsByEmail(registerRequest.getEmail().trim().toLowerCase())) {
            throw new DuplicateResourceException("Email already exists");
        }
    }

    private User createUserFromRegistration(UserRequestDTO registerRequest) {
        User user = new User();
        user.setUsername(registerRequest.getUsername().trim());
        user.setEmail(registerRequest.getEmail().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setFirstName(StringUtils.hasText(registerRequest.getFirstName()) ?
                registerRequest.getFirstName().trim() : null);
        user.setLastName(StringUtils.hasText(registerRequest.getLastName()) ?
                registerRequest.getLastName().trim() : null);
        user.setRole(User.Role.USER);
        user.setActive(true);
        user.setEmailVerified(false);

        user = userRepository.save(user);

        processCartItems(registerRequest, user);
        processFavorites(registerRequest, user);

        return userRepository.save(user);
    }

    private void processCartItems(UserRequestDTO request, User user) {
        if (request.getCartItems() != null) {
            Set<CartItem> cartItems = request.getCartItems().stream()
                    .map(cartRequest -> createCartItem(cartRequest, user))
                    .collect(Collectors.toSet());
            user.setCartItems(cartItems);
        }
    }

    private void processFavorites(UserRequestDTO request, User user) {
        if (request.getUserFavorites() != null) {
            Set<UserFavorite> favorites = request.getUserFavorites().stream()
                    .map(productId -> createUserFavorite(productId, user))
                    .collect(Collectors.toSet());
            user.setFavorites(favorites);
        }
    }

    private CartItem createCartItem(CartItemRequestDto request, User user) {
        Product product = productService.findProductByIdOrThrow(request.getProductId());
        CartItem cartItem = new CartItem();
        cartItem.setUser(user);
        cartItem.setProduct(product);
        cartItem.setQuantity(request.getQuantity());
        return cartItem;
    }

    private UserFavorite createUserFavorite(Long productId, User user) {
        Product product = productService.findProductByIdOrThrow(productId);
        UserFavorite favorite = new UserFavorite();
        favorite.setUser(user);
        favorite.setProduct(product);
        return favorite;
    }

    private void updateLastLogin(User user) {
        try {
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        } catch (Exception e) {
            log.error("Failed to update last login time for user {}: {}", user.getUsername(), e.getMessage());
            // Don't throw exception - this is not critical for login success
        }
    }

    private void sendVerificationEmailSafely(User user) {
        try {
            String verificationToken = jwtUtil.generateEmailVerificationToken(user);
            // In a real implementation, you would send an email here
            log.info("Verification email would be sent to: {} with token: {}", user.getEmail(), verificationToken);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", user.getEmail(), e.getMessage());
            // Don't throw exception - user is created successfully, they can request resend
        }
    }

    private void sendPasswordResetEmailSafely(User user, String resetToken) {
        try {
            // In a real implementation, you would send an email here
            log.info("Password reset email would be sent to: {} with token: {}", user.getEmail(), resetToken);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", user.getEmail(), e.getMessage());
            // Don't throw exception - just log the error
        }
    }

    private UserResponseDTO convertToUserResponseDTO(User user) {
        return UserResponseDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .active(user.getActive())
                .emailVerified(user.getEmailVerified())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .fullName(user.getFullName())
                .build();
    }
}