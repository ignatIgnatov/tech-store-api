package com.techstore.service;

import com.techstore.dto.request.UserRequestDTO;
import com.techstore.dto.UserResponseDTO;
import com.techstore.entity.User;
import com.techstore.exception.DuplicateResourceException;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Page<UserResponseDTO> getAllUsers(Pageable pageable) {
        return userRepository.findByActiveTrue(pageable)
                .map(this::convertToResponseDTO);
    }

    @Transactional(readOnly = true)
    public UserResponseDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return convertToResponseDTO(user);
    }

    @Transactional(readOnly = true)
    public UserResponseDTO getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
        return convertToResponseDTO(user);
    }

    public UserResponseDTO createUser(UserRequestDTO requestDTO) {
        log.info("Creating new user with username: {}", requestDTO.getUsername());

        if (userRepository.existsByUsername(requestDTO.getUsername())) {
            throw new DuplicateResourceException("User with username '" + requestDTO.getUsername() + "' already exists");
        }

        if (userRepository.existsByEmail(requestDTO.getEmail())) {
            throw new DuplicateResourceException("User with email '" + requestDTO.getEmail() + "' already exists");
        }

        User user = convertToEntity(requestDTO);
        user = userRepository.save(user);

        log.info("User created successfully with id: {}", user.getId());
        return convertToResponseDTO(user);
    }

    public UserResponseDTO updateUser(Long id, UserRequestDTO requestDTO) {
        log.info("Updating user with id: {}", id);

        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        if (userRepository.existsByUsernameAndIdNot(requestDTO.getUsername(), id)) {
            throw new DuplicateResourceException("User with username '" + requestDTO.getUsername() + "' already exists");
        }

        if (userRepository.existsByEmailAndIdNot(requestDTO.getEmail(), id)) {
            throw new DuplicateResourceException("User with email '" + requestDTO.getEmail() + "' already exists");
        }

        updateUserFromDTO(existingUser, requestDTO);
        User updatedUser = userRepository.save(existingUser);

        log.info("User updated successfully with id: {}", id);
        return convertToResponseDTO(updatedUser);
    }

    public void deleteUser(Long id) {
        log.info("Deleting user with id: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        user.setActive(false);
        userRepository.save(user);

        log.info("User soft deleted successfully with id: {}", id);
    }

    private User convertToEntity(UserRequestDTO dto) {
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setRole(User.Role.valueOf(dto.getRole()));
        user.setActive(dto.getActive());
        return user;
    }

    private void updateUserFromDTO(User user, UserRequestDTO dto) {
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setRole(User.Role.valueOf(dto.getRole()));
        user.setActive(dto.getActive());
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
