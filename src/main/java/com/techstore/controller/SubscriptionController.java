package com.techstore.controller;

import com.techstore.dto.response.SubscriptionDto;
import com.techstore.service.SubscriptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Subscriptions", description = "Subscription management APIs")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    public ResponseEntity<List<SubscriptionDto>> getAllSubscriptions() {
        List<SubscriptionDto> response = subscriptionService.getAllSubscriptions();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<SubscriptionDto> subscribe(@PathVariable("email") String email) {
        log.info("Subscribed: {}", email);
        SubscriptionDto response = subscriptionService.subscribe(email);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping
    public ResponseEntity<String> unsubscribe(@PathVariable("email") String email) {
        log.info("Unsubscribed: {}", email);
        subscriptionService.unsubscribe(email);
        return ResponseEntity.ok("Unsubscribed: " + email);
    }
}
