package com.techstore.repository;

import com.techstore.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * Намира всички items в поръчка
     */
    List<OrderItem> findByOrderId(Long orderId);

    /**
     * Намира всички поръчки за продукт
     */
    List<OrderItem> findByProductId(Long productId);

    /**
     * Брой продажби за продукт
     */
    @Query("SELECT SUM(oi.quantity) FROM OrderItem oi " +
            "WHERE oi.product.id = :productId")
    Long getTotalQuantitySoldForProduct(@Param("productId") Long productId);

    /**
     * Топ продавани продукти
     */
    @Query("SELECT oi.product.id, oi.product.nameBg, SUM(oi.quantity) as totalSold " +
            "FROM OrderItem oi " +
            "GROUP BY oi.product.id, oi.product.nameBg " +
            "ORDER BY totalSold DESC")
    List<Object[]> getTopSellingProducts();

    /**
     * Общ приход от продукт
     */
    @Query("SELECT SUM(oi.lineTotal + oi.lineTax) FROM OrderItem oi " +
            "WHERE oi.product.id = :productId")
    java.math.BigDecimal getTotalRevenueForProduct(@Param("productId") Long productId);
}