package com.techstore.config;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties
public class SearchConfig {

    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @ConfigurationProperties(prefix = "app.search.postgresql")
    @Data
    public static class PostgreSQLSearchProperties {
        private boolean enableFullTextSearch = true;
        private int defaultPageSize = 20;
        private int maxPageSize = 100;
        private int maxQueryLength = 200;
        private int suggestionsCacheMinutes = 30;
        private boolean enableQueryLogging = false;
    }

    @Bean
    @ConditionalOnProperty(name = "app.search.postgresql.auto-create-indexes", havingValue = "true", matchIfMissing = true)
    public SearchIndexManager searchIndexManager(JdbcTemplate jdbcTemplate) {
        return new SearchIndexManager(jdbcTemplate);
    }
}
