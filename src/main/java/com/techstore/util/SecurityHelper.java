package com.techstore.util;

import com.techstore.entity.User;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityHelper {

    private final UserRepository userRepository;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserDetails) {
            String username = ((UserDetails) principal).getUsername();
            return userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        }

        throw new IllegalStateException("Invalid authentication principal");
    }

    public Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }

        return null;
    }

    public boolean isCurrentUserAdmin() {
        try {
            User user = getCurrentUser();
            return user.isAdmin();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isCurrentUserOwner(Long userId) {
        try {
            return getCurrentUserId().equals(userId);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasAccessToResource(Long userId) {
        return isCurrentUserOwner(userId) || isCurrentUserAdmin();
    }
}