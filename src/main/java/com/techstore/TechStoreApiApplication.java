package com.techstore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
public class TechStoreApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(TechStoreApiApplication.class, args);
    }
}