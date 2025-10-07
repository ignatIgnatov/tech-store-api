package com.techstore.repository;

import com.techstore.entity.Order;
import com.techstore.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Намира поръчка по номер
     */
    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * Намира всички поръчки на потребител
     */
    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Брой поръчки на потребител
     */
    long countByUserId(Long userId);

    /**
     * Намира поръчки по статус
     */
    List<Order> findByStatus(OrderStatus status);

    /**
     * Намира поръчки по email на клиента
     */
    List<Order> findByCustomerEmail(String email);

    /**
     * Проверява дали има поръчки за продукт
     */
    @Query("SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END " +
            "FROM OrderItem oi WHERE oi.product.id = :productId")
    boolean existsOrderWithProduct(@Param("productId") Long productId);

    /**
     * Намира поръчки за определен месец и година (за NAP генериране)
     */
    @Query("SELECT o FROM Order o " +
            "WHERE YEAR(o.createdAt) = :year " +
            "AND MONTH(o.createdAt) = :month " +
            "ORDER BY o.createdAt ASC")
    List<Order> findOrdersByYearAndMonth(@Param("year") int year, @Param("month") int month);

    /**
     * Намира поръчки в период
     */
    @Query("SELECT o FROM Order o " +
            "WHERE o.createdAt BETWEEN :startDate AND :endDate " +
            "ORDER BY o.createdAt DESC")
    List<Order> findOrdersInDateRange(
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate
    );

    /**
     * Статистика за поръчки по статус
     */
    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> getOrderStatsByStatus();

    /**
     * Общ приход от завършени поръчки
     */
    @Query("SELECT SUM(o.total) FROM Order o " +
            "WHERE o.status = 'DELIVERED' AND o.paymentStatus = 'PAID'")
    java.math.BigDecimal getTotalRevenue();
}