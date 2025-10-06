package com.techstore.service;

import com.techstore.dto.request.OrderCreateRequestDTO;
import com.techstore.dto.request.OrderStatusUpdateDTO;
import com.techstore.dto.response.OrderItemResponseDTO;
import com.techstore.dto.response.OrderResponseDTO;
import com.techstore.entity.*;
import com.techstore.enums.OrderStatus;
import com.techstore.enums.PaymentStatus;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CartItemRepository cartItemRepository;

    /**
     * Създава нова поръчка
     */
    @Transactional
    public OrderResponseDTO createOrder(OrderCreateRequestDTO request, Long userId) {
        log.info("Creating order for user: {}", userId);

        // Намираме потребителя
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Създаваме поръчката
        Order order = new Order();
        order.setUser(user);
        order.setOrderNumber(generateOrderNumber());
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setPaymentMethod(request.getPaymentMethod());

        // Задаваме информация за клиента
        order.setCustomerFirstName(request.getCustomerFirstName());
        order.setCustomerLastName(request.getCustomerLastName());
        order.setCustomerEmail(request.getCustomerEmail());
        order.setCustomerPhone(request.getCustomerPhone());
        order.setCustomerCompany(request.getCustomerCompany());
        order.setCustomerVatNumber(request.getCustomerVatNumber());
        order.setCustomerVatRegistered(request.getCustomerVatRegistered());

        // Адрес за доставка
        order.setShippingAddress(request.getShippingAddress());
        order.setShippingCity(request.getShippingCity());
        order.setShippingPostalCode(request.getShippingPostalCode());
        order.setShippingCountry(request.getShippingCountry());

        // Адрес за фактура
        if (Boolean.TRUE.equals(request.getUseSameAddressForBilling())) {
            order.setBillingAddress(request.getShippingAddress());
            order.setBillingCity(request.getShippingCity());
            order.setBillingPostalCode(request.getShippingPostalCode());
            order.setBillingCountry(request.getShippingCountry());
        } else {
            order.setBillingAddress(request.getBillingAddress());
            order.setBillingCity(request.getBillingCity());
            order.setBillingPostalCode(request.getBillingPostalCode());
            order.setBillingCountry(request.getBillingCountry());
        }

        // Бележки
        order.setCustomerNotes(request.getCustomerNotes());
        order.setShippingCost(request.getShippingCost());

        // Добавяме продуктите
        for (var itemDto : request.getItems()) {
            Product product = productRepository.findById(itemDto.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemDto.getProductId()));

            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setProductName(product.getNameBg());
            orderItem.setProductSku(product.getSku());
            orderItem.setProductModel(product.getModel());
            orderItem.setQuantity(itemDto.getQuantity());
            orderItem.setUnitPrice(product.getPriceClient()); // Цена без ДДС
            orderItem.setTaxRate(new BigDecimal("20.00")); // 20% ДДС

            orderItem.calculateLineTotals();
            order.addOrderItem(orderItem);
        }

        // Изчисляваме общите суми
        order.calculateTotals();

        // Запазваме
        order = orderRepository.save(order);

        // Изчистваме кошницата на потребителя
        cartItemRepository.deleteByUserId(userId);

        log.info("Order created successfully: {}", order.getOrderNumber());

        return mapToResponseDTO(order);
    }

    /**
     * Взема поръчка по ID
     */
    @Transactional(readOnly = true)
    public OrderResponseDTO getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return mapToResponseDTO(order);
    }

    /**
     * Взема поръчка по номер
     */
    @Transactional(readOnly = true)
    public OrderResponseDTO getOrderByNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return mapToResponseDTO(order);
    }

    /**
     * Взема поръчките на потребител
     */
    @Transactional(readOnly = true)
    public Page<OrderResponseDTO> getUserOrders(Long userId, Pageable pageable) {
        Page<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return orders.map(this::mapToResponseDTO);
    }

    /**
     * Взема всички поръчки (админ)
     */
    @Transactional(readOnly = true)
    public Page<OrderResponseDTO> getAllOrders(Pageable pageable) {
        Page<Order> orders = orderRepository.findAll(pageable);
        return orders.map(this::mapToResponseDTO);
    }

    /**
     * Променя статуса на поръчка
     */
    @Transactional
    public OrderResponseDTO updateOrderStatus(Long orderId, OrderStatusUpdateDTO request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        order.setStatus(request.getStatus());

        if (request.getAdminNotes() != null) {
            order.setAdminNotes(request.getAdminNotes());
        }

        if (request.getTrackingNumber() != null) {
            order.setTrackingNumber(request.getTrackingNumber());
        }

        // Автоматично задаване на дати
        if (request.getStatus() == OrderStatus.SHIPPED && order.getShippedAt() == null) {
            order.setShippedAt(LocalDateTime.now());
        }

        if (request.getStatus() == OrderStatus.DELIVERED && order.getDeliveredAt() == null) {
            order.setDeliveredAt(LocalDateTime.now());
        }

        order = orderRepository.save(order);

        log.info("Order {} status updated to {}", order.getOrderNumber(), request.getStatus());

        return mapToResponseDTO(order);
    }

    /**
     * Отказва поръчка
     */
    @Transactional
    public OrderResponseDTO cancelOrder(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new IllegalStateException("Cannot cancel delivered order");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setAdminNotes(order.getAdminNotes() + "\nCancellation reason: " + reason);

        order = orderRepository.save(order);

        log.info("Order {} cancelled", order.getOrderNumber());

        return mapToResponseDTO(order);
    }

    /**
     * Взема поръчки за определен месец (за NAP файл)
     */
    @Transactional(readOnly = true)
    public List<Order> getOrdersForMonth(int year, int month) {
        return orderRepository.findOrdersByYearAndMonth(year, month);
    }

    /**
     * Генерира уникален номер на поръчка
     */
    private String generateOrderNumber() {
        String year = String.valueOf(Year.now().getValue());
        long count = orderRepository.count() + 1;
        return String.format("ORD-%s-%05d", year, count);
    }

    /**
     * Маппа Order към OrderResponseDTO
     */
    private OrderResponseDTO mapToResponseDTO(Order order) {
        List<OrderItemResponseDTO> items = order.getOrderItems().stream()
                .map(this::mapItemToResponseDTO)
                .collect(Collectors.toList());

        return OrderResponseDTO.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerFirstName(order.getCustomerFirstName())
                .customerLastName(order.getCustomerLastName())
                .customerEmail(order.getCustomerEmail())
                .customerPhone(order.getCustomerPhone())
                .customerCompany(order.getCustomerCompany())
                .customerVatNumber(order.getCustomerVatNumber())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .paymentMethod(order.getPaymentMethod())
                .subtotal(order.getSubtotal())
                .taxAmount(order.getTaxAmount())
                .shippingCost(order.getShippingCost())
                .discountAmount(order.getDiscountAmount())
                .total(order.getTotal())
                .shippingAddress(order.getShippingAddress())
                .shippingCity(order.getShippingCity())
                .shippingPostalCode(order.getShippingPostalCode())
                .shippingCountry(order.getShippingCountry())
                .billingAddress(order.getBillingAddress())
                .billingCity(order.getBillingCity())
                .billingPostalCode(order.getBillingPostalCode())
                .billingCountry(order.getBillingCountry())
                .items(items)
                .customerNotes(order.getCustomerNotes())
                .adminNotes(order.getAdminNotes())
                .trackingNumber(order.getTrackingNumber())
                .shippedAt(order.getShippedAt())
                .deliveredAt(order.getDeliveredAt())
                .invoiceNumber(order.getInvoiceNumber())
                .invoiceDate(order.getInvoiceDate())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private OrderItemResponseDTO mapItemToResponseDTO(OrderItem item) {
        return OrderItemResponseDTO.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProductName())
                .productSku(item.getProductSku())
                .productModel(item.getProductModel())
                .productImageUrl(item.getProduct().getPrimaryImageUrl())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .taxRate(item.getTaxRate())
                .lineTotal(item.getLineTotal())
                .lineTax(item.getLineTax())
                .discountAmount(item.getDiscountAmount())
                .build();
    }
}