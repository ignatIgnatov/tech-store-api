package com.techstore.service;

import com.techstore.dto.response.SubscriptionDto;
import com.techstore.entity.Subscription;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    public List<SubscriptionDto> getAllSubscriptions() {
        return subscriptionRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();

    }

    public SubscriptionDto subscribe(String email) {
        Subscription subscription = new Subscription();
        subscription.setEmail(email);
        Subscription createdSubscription = subscriptionRepository.save(subscription);
        return mapToResponse(createdSubscription);
    }

    public void unsubscribe(String email) {
        Subscription subscription = subscriptionRepository.findByEmail(email).orElseThrow(
                () -> new ResourceNotFoundException("Subscription not found with email " + email)
        );
        subscriptionRepository.delete(subscription);
    }

    private SubscriptionDto mapToResponse(Subscription subscription) {
        return SubscriptionDto.builder()
                .id(subscription.getId())
                .email(subscription.getEmail())
                .build();
    }

    public Boolean isSubscribed(String email) {
        return subscriptionRepository.existsByEmail(email);
    }
}
