package com.techstore.service;

import com.techstore.dto.UserResponseDTO;
import com.techstore.dto.request.UserRequestDTO;
import com.techstore.entity.User;
import com.techstore.exception.BusinessLogicException;
import com.techstore.exception.DuplicateResourceException;
import com.techstore.exception.ValidationException;
import com.techstore.repository.UserRepository;
import com.techstore.util.ExceptionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Validation patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"
    );
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,100}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$"
    );

    @Transactional(readOnly = true)
    public Page<UserResponseDTO> getAllUsers(Pageable pageable) {
        log.debug("Fetching all active users - Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());

        return ExceptionHelper.wrapDatabaseOperation(() ->
                        userRepository.findByActiveTrue(pageable).map(this::convertToResponseDTO),
                "fetch all users"
        );
    }

    @Transactional(readOnly = true)
    public UserResponseDTO getUserById(Long id) {
        log.debug("Fetching user with id: {}", id);

        validateUserId(id);

        User user = findUserByIdOrThrow(id);
        return convertToResponseDTO(user);
    }

    @Transactional(readOnly = true)
    public UserResponseDTO getUserByUsername(String username) {
        log.debug("Fetching user with username: {}", username);

        validateUsername(username, false);

        User user = ExceptionHelper.findOrThrow(
                userRepository.findByUsername(username).orElse(null),
                "User",
                "username: " + username
        );

        return convertToResponseDTO(user);
    }

    public UserResponseDTO createUser(UserRequestDTO requestDTO) {
        log.info("Creating new user with username: {}", requestDTO.getUsername());

        String context = ExceptionHelper.createErrorContext(
                "createUser", "User", null, requestDTO.getUsername());

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            // Comprehensive validation
            validateUserRequest(requestDTO, true);

            // Check for duplicates
            checkForDuplicateUser(requestDTO);

            // Create user
            User user = createUserFromRequest(requestDTO);
            user = userRepository.save(user);

            log.info("User created successfully with id: {} and username: {}",
                    user.getId(), user.getUsername());

            return convertToResponseDTO(user);

        }, context);
    }

    public UserResponseDTO updateUser(Long id, UserRequestDTO requestDTO) {
        log.info("Updating user with id: {}", id);

        String context = ExceptionHelper.createErrorContext("updateUser", "User", id, null);

        return ExceptionHelper.wrapDatabaseOperation(() -> {
            // Validate inputs
            validateUserId(id);
            validateUserRequest(requestDTO, false);

            // Find existing user
            User existingUser = findUserByIdOrThrow(id);

            // Check for username/email conflicts
            checkForUserConflicts(requestDTO, id);

            // Update user
            updateUserFromRequest(existingUser, requestDTO);
            User updatedUser = userRepository.save(existingUser);

            log.info("User updated successfully with id: {}", id);
            return convertToResponseDTO(updatedUser);

        }, context);
    }

    public void deleteUser(Long id) {
        log.info("Deleting user with id: {}", id);

        String context = ExceptionHelper.createErrorContext("deleteUser", "User", id, null);

        ExceptionHelper.wrapDatabaseOperation(() -> {
            validateUserId(id);

            User user = findUserByIdOrThrow(id);

            // Business validation for deletion
            validateUserDeletion(user);

            // Soft delete
            user.setActive(false);
            userRepository.save(user);

            log.info("User soft deleted successfully with id: {}", id);
            return null;
        }, context);
    }

    public void permanentDeleteUser(Long id) {
        log.warn("Permanently deleting user with id: {}", id);

        String context = ExceptionHelper.createErrorContext("permanentDeleteUser", "User", id, null);

        ExceptionHelper.wrapDatabaseOperation(() -> {
            validateUserId(id);

            User user = findUserByIdOrThrow(id);

            // Strict validation for permanent deletion
            validatePermanentUserDeletion(user);

            userRepository.deleteById(id);

            log.warn("User permanently deleted with id: {}", id);
            return null;
        }, context);
    }

    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String username) {
        if (!StringUtils.hasText(username)) {
            return false;
        }

        return !userRepository.existsByUsername(username.trim());
    }

    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }

        return !userRepository.existsByEmail(email.trim().toLowerCase());
    }

    public void changeUserStatus(Long id, boolean active) {
        log.info("Changing user status - ID: {}, Active: {}", id, active);

        String context = ExceptionHelper.createErrorContext("changeUserStatus", "User", id, "active: " + active);

        ExceptionHelper.wrapDatabaseOperation(() -> {
            validateUserId(id);

            User user = findUserByIdOrThrow(id);

            if (user.getActive().equals(active)) {
                throw new BusinessLogicException(
                        String.format("User is already %s", active ? "active" : "inactive"));
            }

            user.setActive(active);
            userRepository.save(user);

            log.info("User status changed successfully - ID: {}, New status: {}", id, active);
            return null;
        }, context);
    }

    // ========== PRIVATE VALIDATION METHODS ==========

    private void validateUserId(Long id) {
        if (id == null || id <= 0) {
            throw new ValidationException("User ID must be a positive number");
        }
    }

    private void validateUserRequest(UserRequestDTO requestDTO, boolean isCreate) {
        if (requestDTO == null) {
            throw new ValidationException("User request cannot be null");
        }

        // Validate username
        validateUsername(requestDTO.getUsername(), true);

        // Validate email
        validateEmail(requestDTO.getEmail());

        // Validate password (required for create, optional for update)
        if (isCreate || StringUtils.hasText(requestDTO.getPassword())) {
            validatePassword(requestDTO.getPassword());
        }

        // Validate role
        validateRole(requestDTO.getRole());

        // Validate optional fields
        validateOptionalFields(requestDTO);
    }

    private void validateUsername(String username, boolean isForRequest) {
        if (!StringUtils.hasText(username)) {
            throw new ValidationException("Username is required");
        }

        String trimmed = username.trim();

        if (trimmed.length() < 3) {
            throw new ValidationException("Username must be at least 3 characters long");
        }

        if (trimmed.length() > 100) {
            throw new ValidationException("Username cannot exceed 100 characters");
        }

        if (isForRequest && !USERNAME_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException(
                    "Username can only contain letters, numbers, and underscores");
        }
    }

    private void validateEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new ValidationException("Email is required");
        }

        String trimmed = email.trim().toLowerCase();

        if (trimmed.length() > 200) {
            throw new ValidationException("Email cannot exceed 200 characters");
        }

        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
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

        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new ValidationException(
                    "Password must contain at least one uppercase letter, one lowercase letter, " +
                            "one number, and one special character (@$!%*?&)");
        }

        // Check for common weak passwords
        String[] weakPasswords = {"password", "12345678", "password123", "admin123"};
        if (Arrays.stream(weakPasswords).anyMatch(weak -> weak.equalsIgnoreCase(password))) {
            throw new ValidationException("Password is too common. Please choose a stronger password.");
        }
    }

    private void validateRole(String role) {
        if (!StringUtils.hasText(role)) {
            throw new ValidationException("Role is required");
        }

        try {
            User.Role.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(
                    "Invalid role. Valid roles are: " + Arrays.toString(User.Role.values()));
        }
    }

    private void validateOptionalFields(UserRequestDTO requestDTO) {
        if (StringUtils.hasText(requestDTO.getFirstName()) && requestDTO.getFirstName().length() > 100) {
            throw new ValidationException("First name cannot exceed 100 characters");
        }

        if (StringUtils.hasText(requestDTO.getLastName()) && requestDTO.getLastName().length() > 100) {
            throw new ValidationException("Last name cannot exceed 100 characters");
        }
    }

    private void validateUserDeletion(User user) {
        // Check if user is super admin
        if (user.isSuperAdmin()) {
            throw new BusinessLogicException("Cannot delete super admin users");
        }

        // Check if user has important data that would be lost
        // (You might want to check for orders, favorites, etc.)
        validateUserDataDependencies(user);
    }

    private void validatePermanentUserDeletion(User user) {
        validateUserDeletion(user);

        // Additional strict validation for permanent deletion
        if (user.getActive()) {
            throw new BusinessLogicException("User must be deactivated before permanent deletion");
        }

        // Could add additional checks like "deleted more than 30 days ago"
    }

    private void validateUserDataDependencies(User user) {
        // Check for user's cart items
        if (user.getCartItems() != null && !user.getCartItems().isEmpty()) {
            log.info("User {} has {} cart items that will be deleted", user.getId(), user.getCartItems().size());
        }

        // Check for user's favorites
        if (user.getFavorites() != null && !user.getFavorites().isEmpty()) {
            log.info("User {} has {} favorites that will be deleted", user.getId(), user.getFavorites().size());
        }

        // In a real application, you might want to check for orders, reviews, etc.
    }

    // ========== PRIVATE HELPER METHODS ==========

    private User findUserByIdOrThrow(Long id) {
        return ExceptionHelper.findOrThrow(
                userRepository.findById(id).orElse(null),
                "User",
                id
        );
    }

    private void checkForDuplicateUser(UserRequestDTO requestDTO) {
        if (userRepository.existsByUsername(requestDTO.getUsername().trim())) {
            throw new DuplicateResourceException(
                    "User with username '" + requestDTO.getUsername() + "' already exists");
        }

        if (userRepository.existsByEmail(requestDTO.getEmail().trim().toLowerCase())) {
            throw new DuplicateResourceException(
                    "User with email '" + requestDTO.getEmail() + "' already exists");
        }
    }

    private void checkForUserConflicts(UserRequestDTO requestDTO, Long userId) {
        if (userRepository.existsByUsernameAndIdNot(requestDTO.getUsername().trim(), userId)) {
            throw new DuplicateResourceException(
                    "User with username '" + requestDTO.getUsername() + "' already exists");
        }

        if (userRepository.existsByEmailAndIdNot(requestDTO.getEmail().trim().toLowerCase(), userId)) {
            throw new DuplicateResourceException(
                    "User with email '" + requestDTO.getEmail() + "' already exists");
        }
    }

    private User createUserFromRequest(UserRequestDTO requestDTO) {
        User user = new User();

        user.setUsername(requestDTO.getUsername().trim());
        user.setEmail(requestDTO.getEmail().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(requestDTO.getPassword()));
        user.setFirstName(StringUtils.hasText(requestDTO.getFirstName()) ? requestDTO.getFirstName().trim() : null);
        user.setLastName(StringUtils.hasText(requestDTO.getLastName()) ? requestDTO.getLastName().trim() : null);
        user.setRole(User.Role.valueOf(requestDTO.getRole().toUpperCase()));
        user.setActive(requestDTO.getActive() != null ? requestDTO.getActive() : true);
        user.setEmailVerified(false); // New users must verify email

        return user;
    }

    private void updateUserFromRequest(User user, UserRequestDTO requestDTO) {
        user.setUsername(requestDTO.getUsername().trim());
        user.setEmail(requestDTO.getEmail().trim().toLowerCase());

        // Only update password if provided
        if (StringUtils.hasText(requestDTO.getPassword())) {
            user.setPassword(passwordEncoder.encode(requestDTO.getPassword()));
        }

        user.setFirstName(StringUtils.hasText(requestDTO.getFirstName()) ? requestDTO.getFirstName().trim() : null);
        user.setLastName(StringUtils.hasText(requestDTO.getLastName()) ? requestDTO.getLastName().trim() : null);
        user.setRole(User.Role.valueOf(requestDTO.getRole().toUpperCase()));

        if (requestDTO.getActive() != null) {
            user.setActive(requestDTO.getActive());
        }
    }

    private UserResponseDTO convertToResponseDTO(User user) {
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