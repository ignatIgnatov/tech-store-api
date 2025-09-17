package com.techstore.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    private Sync sync = new Sync();
    private Markup markup = new Markup();

    @Data
    public static class Sync {
        private boolean enabled = true;
        private String cron = "0 0 2 * * ?";
        private int batchSize = 100;
    }

    @Data
    public static class Markup {
        private BigDecimal defaultPercentage = BigDecimal.valueOf(20.0);
        private BigDecimal maxPercentage = BigDecimal.valueOf(200.0);
        private BigDecimal minPercentage = BigDecimal.valueOf(-50.0);
    }
}