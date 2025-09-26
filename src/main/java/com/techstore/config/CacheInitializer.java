package com.techstore.config;

import jakarta.annotation.PostConstruct;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
public class CacheInitializer {

    private final CacheManager cacheManager;

    public CacheInitializer(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @PostConstruct
    public void init() {
        // Initialize all required caches
        String[] cacheNames = {
                "products", "manufacturers", "parameters", "parameterOptions",
                "categoriesByExternalId", "manufacturersByExternalId",
                "parametersByCategory", "productsByCategory"
        };

        for (String cacheName : cacheNames) {
            cacheManager.getCache(cacheName);
        }
    }
}
