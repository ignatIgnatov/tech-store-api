package com.techstore.repository;

import com.techstore.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Find by username or email
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsernameOrEmail(String username, String email);

    // Check if exists
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsernameAndIdNot(String username, Long id);
    boolean existsByEmailAndIdNot(String email, Long id);

    // Find active users
    Page<User> findByActiveTrue(Pageable pageable);
    List<User> findByActiveTrueOrderByCreatedAtDesc();

    // Find by role
    Page<User> findByRole(User.Role role, Pageable pageable);
    List<User> findByRoleAndActiveTrue(User.Role role);

    // Find admins
    @Query("SELECT u FROM User u WHERE u.active = true AND " +
            "(u.role = 'ADMIN' OR u.role = 'SUPER_ADMIN') ORDER BY u.createdAt DESC")
    List<User> findActiveAdmins();

    // Search users
    @Query("SELECT u FROM User u WHERE " +
            "(LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<User> searchUsers(@Param("query") String query, Pageable pageable);
}
