package com.techstore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "speedy")
public class SpeedyConfig {
    private String baseUrl;
    private String userName;
    private String password;
    private Long senderSiteId;
    private Long defaultServiceId;
    private Boolean saturdayDelivery;
}