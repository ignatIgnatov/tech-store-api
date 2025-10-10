package com.techstore.controller;

import com.techstore.dto.request.OrderCreateRequestDTO;
import com.techstore.dto.request.OrderStatusUpdateDTO;
import com.techstore.dto.response.OrderResponseDTO;
import com.techstore.entity.User;
import com.techstore.service.OrderService;
import com.techstore.util.SecurityHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;
    private final SecurityHelper securityHelper;

    /**
     * Създаване на нова поръчка
     */
    @PostMapping
    public ResponseEntity<OrderResponseDTO> createOrder(
            @Valid @RequestBody OrderCreateRequestDTO request) {
        OrderResponseDTO order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    /**
     * Взема поръчка по ID
     */
    @GetMapping("/{orderId}")
//    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponseDTO> getOrderById(
            @PathVariable Long orderId) {

        User currentUser = securityHelper.getCurrentUser();
        OrderResponseDTO order = orderService.getOrderById(orderId);

        // Проверка дали потребителят има право да вижда тази поръчка
        // (само собственик или админ)
        if (!currentUser.isAdmin() && !order.getCustomerEmail().equals(currentUser.getEmail())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(order);
    }

    /**
     * Взема поръчка по номер
     */
    @GetMapping("/number/{orderNumber}")
//    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponseDTO> getOrderByNumber(
            @PathVariable String orderNumber) {

        User currentUser = securityHelper.getCurrentUser();
        OrderResponseDTO order = orderService.getOrderByNumber(orderNumber);

        if (!currentUser.isAdmin() && !order.getCustomerEmail().equals(currentUser.getEmail())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(order);
    }

    /**
     * Взема поръчките на текущия потребител
     */
    @GetMapping("/my-orders")
//    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<OrderResponseDTO>> getMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        User currentUser = securityHelper.getCurrentUser();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<OrderResponseDTO> orders = orderService.getUserOrders(currentUser.getId(), pageable);
        return ResponseEntity.ok(orders);
    }

    /**
     * Взема всички поръчки (само админ)
     */
    @GetMapping("/admin/all")
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<OrderResponseDTO>> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<OrderResponseDTO> orders = orderService.getAllOrders(pageable);
        return ResponseEntity.ok(orders);
    }

    /**
     * Променя статуса на поръчка (само админ)
     */
    @PutMapping("/{orderId}/status")
//    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponseDTO> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderStatusUpdateDTO request) {

        OrderResponseDTO order = orderService.updateOrderStatus(orderId, request);
        return ResponseEntity.ok(order);
    }

    /**
     * Отказва поръчка
     */
    @PostMapping("/{orderId}/cancel")
//    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponseDTO> cancelOrder(
            @PathVariable Long orderId,
            @RequestParam String reason) {

        User currentUser = securityHelper.getCurrentUser();
        OrderResponseDTO order = orderService.getOrderById(orderId);

        // Само собственик или админ могат да откажат
        if (!currentUser.isAdmin() && !order.getCustomerEmail().equals(currentUser.getEmail())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        OrderResponseDTO cancelledOrder = orderService.cancelOrder(orderId, reason);
        return ResponseEntity.ok(cancelledOrder);
    }
}