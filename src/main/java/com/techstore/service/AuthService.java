package com.techstore.service;

import com.techstore.dto.*;
import com.techstore.entity.User;
import com.techstore.repository.UserRepository;
import com.techstore.exception.*;
import com.techstore.util.JwtUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public LoginResponseDTO login(LoginRequestDTO loginRequest) {
        try {
            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsernameOrEmail(),
                            loginRequest.getPassword()
                    )
            );

            User user = (User) authentication.getPrincipal();

            // Check if account is active
            if (!user.getActive()) {
                throw new AccountNotActivatedException("Account is deactivated");
            }

            // Check if email is verified
            if (!user.getEmailVerified()) {
                throw new AccountNotActivatedException("Email not verified. Please verify your email first.");
            }

            // Generate JWT token
            String token = jwtUtil.generateToken(user);

            // Update last login
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);

            log.info("User {} logged in successfully", user.getUsername());

            return LoginResponseDTO.builder()
                    .token(token)
                    .type("Bearer")
                    .user(convertToUserResponseDTO(user))
                    .build();

        } catch (AuthenticationException e) {
            log.error("Authentication failed for user: {}", loginRequest.getUsernameOrEmail());
            throw new InvalidCredentialsException("Invalid username/email or password");
        }
    }

    public UserResponseDTO register(UserRequestDTO registerRequest) {
        // Check if username already exists
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new DuplicateResourceException("Username already exists");
        }

        // Check if email already exists
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new DuplicateResourceException("Email already exists");
        }

        // Create new user
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setFirstName(registerRequest.getFirstName());
        user.setLastName(registerRequest.getLastName());
        user.setRole(User.Role.USER); // Default role
        user.setActive(true);
        user.setEmailVerified(false);

        user = userRepository.save(user);

        // Send verification email (implementation would depend on email service)
        sendVerificationEmail(user);

        log.info("User {} registered successfully", user.getUsername());

        return convertToUserResponseDTO(user);
    }

    public void logout(String token) {
        // In a real implementation, you might want to blacklist the token
        // For now, we'll just log the logout
        log.info("User logged out");
    }

    public LoginResponseDTO refreshToken(String token) {
        try {
            // Remove "Bearer " prefix if present
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            String username = jwtUtil.extractUsername(token);
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            if (jwtUtil.isTokenValid(token, user)) {
                String newToken = jwtUtil.generateToken(user);

                return LoginResponseDTO.builder()
                        .token(newToken)
                        .type("Bearer")
                        .user(convertToUserResponseDTO(user))
                        .build();
            } else {
                throw new InvalidTokenException("Invalid or expired token");
            }

        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getMessage());
            throw new InvalidTokenException("Token refresh failed");
        }
    }

    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        // Generate password reset token
        String resetToken = jwtUtil.generatePasswordResetToken(user);

        // Send password reset email (implementation would depend on email service)
        sendPasswordResetEmail(user, resetToken);

        log.info("Password reset email sent to: {}", email);
    }

    public void resetPassword(String token, String newPassword) {
        try {
            String username = jwtUtil.extractUsername(token);
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            if (jwtUtil.isPasswordResetTokenValid(token)) {
                user.setPassword(passwordEncoder.encode(newPassword));
                userRepository.save(user);

                log.info("Password reset successfully for user: {}", username);
            } else {
                throw new InvalidTokenException("Invalid or expired reset token");
            }

        } catch (Exception e) {
            log.error("Password reset failed: {}", e.getMessage());
            throw new InvalidTokenException("Password reset failed");
        }
    }

    public void verifyEmail(String token) {
        try {
            String username = jwtUtil.extractUsername(token);
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            if (jwtUtil.isEmailVerificationTokenValid(token)) {
                user.setEmailVerified(true);
                userRepository.save(user);

                log.info("Email verified successfully for user: {}", username);
            } else {
                throw new InvalidTokenException("Invalid or expired verification token");
            }

        } catch (Exception e) {
            log.error("Email verification failed: {}", e.getMessage());
            throw new InvalidTokenException("Email verification failed");
        }
    }

    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        if (user.getEmailVerified()) {
            throw new BusinessLogicException("Email is already verified");
        }

        sendVerificationEmail(user);
        log.info("Verification email resent to: {}", email);
    }

    // Helper methods
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

    private void sendVerificationEmail(User user) {
        // Generate verification token
        String verificationToken = jwtUtil.generateEmailVerificationToken(user);

        // In a real implementation, you would send an email here
        // EmailService.sendVerificationEmail(user.getEmail(), verificationToken);

        log.info("Verification email would be sent to: {} with token: {}", user.getEmail(), verificationToken);
    }

    private void sendPasswordResetEmail(User user, String resetToken) {
        // In a real implementation, you would send an email here
        // EmailService.sendPasswordResetEmail(user.getEmail(), resetToken);

        log.info("Password reset email would be sent to: {} with token: {}", user.getEmail(), resetToken);
    }
}