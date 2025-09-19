package com.techstore.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "parametersByCategory",
                "optionsByParameter",
                "categoriesByExternalId",
                "manufacturersByExternalId",
                "parameters",
                "productsByCategory",
                "parameterOptions"
        );
        cacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofHours(1))
                        .maximumSize(10_000)
                        .recordStats()
        );
        return cacheManager;
    }


}