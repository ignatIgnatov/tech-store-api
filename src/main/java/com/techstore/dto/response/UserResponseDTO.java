package com.techstore.dto;

import com.techstore.dto.response.CartItemResponseDto;
import com.techstore.dto.response.UserFavoriteResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private Boolean active;
    private Boolean emailVerified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
    private String fullName;
    private Set<CartItemResponseDto> cartItems;
    private List<UserFavoriteResponseDto> userFavorites;
}
