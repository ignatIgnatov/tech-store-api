package com.techstore.repository;

import com.techstore.entity.UserFavorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Long> {

    @Query("SELECT uf FROM UserFavorite uf JOIN FETCH uf.product p " +
            "WHERE uf.user.id = :userId AND p.show = true")
    Page<UserFavorite> findByUserIdWithProducts(@Param("userId") Long userId, Pageable pageable);

    Optional<UserFavorite> findByUserIdAndProductId(Long userId, Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    void deleteByUserIdAndProductId(Long userId, Long productId);

    Long countByUserId(Long userId);

    void deleteByUserId(Long userId);
}