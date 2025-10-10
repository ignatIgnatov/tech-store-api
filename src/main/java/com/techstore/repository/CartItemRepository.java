package com.techstore.repository;

import com.techstore.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUserIdOrderByCreatedAtAsc(Long userId);

    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

    void deleteByUserIdAndProductId(Long userId, Long productId);

    void deleteByUserId(Long userId);

    void deleteByUserEmail(String email);

    Long countByUserId(Long userId);

    @Query("SELECT SUM(ci.quantity * p.finalPrice) FROM CartItem ci " +
            "JOIN ci.product p WHERE ci.user.id = :userId")
    BigDecimal calculateTotalPriceByUserId(@Param("userId") Long userId);
}