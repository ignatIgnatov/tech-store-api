package com.techstore.controller;

import com.techstore.dto.UserResponseDTO;
import com.techstore.dto.request.LoginRequestDTO;
import com.techstore.dto.request.UserRequestDTO;
import com.techstore.dto.response.LoginResponseDTO;
import com.techstore.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequest, @RequestParam(defaultValue = "en") String language) {
        log.info("Login attempt for user: {}", loginRequest.getUsernameOrEmail());
        LoginResponseDTO response = authService.login(loginRequest, language);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> register(@Valid @RequestBody UserRequestDTO registerRequest, @RequestParam(defaultValue = "en") String language) {
        log.info("Registration attempt for user: {}", registerRequest.getUsername());
        UserResponseDTO response = authService.register(registerRequest, language);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String token) {
        log.info("Logout request received");
        authService.logout(token);
        return ResponseEntity.ok("Logged out successfully");
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDTO> refreshToken(@RequestHeader("Authorization") String token, @RequestParam(defaultValue = "en") String language) {
        log.info("Token refresh request received");
        LoginResponseDTO response = authService.refreshToken(token, language);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestParam String email) {
        log.info("Password reset request for email: {}", email);
        authService.forgotPassword(email);
        return ResponseEntity.ok("Password reset instructions sent to your email");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(
            @RequestParam String token,
            @RequestParam String newPassword) {
        log.info("Password reset attempt with token");
        authService.resetPassword(token, newPassword);
        return ResponseEntity.ok("Password reset successfully");
    }

    @PostMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        log.info("Email verification attempt with token");
        authService.verifyEmail(token);
        return ResponseEntity.ok("Email verified successfully");
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<String> resendVerificationEmail(@RequestParam String email) {
        log.info("Resend verification email request for: {}", email);
        authService.resendVerificationEmail(email);
        return ResponseEntity.ok("Verification email sent");
    }
}