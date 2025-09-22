package com.techstore.config;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.techstore.mapper.TekraMapper;
import com.techstore.service.TekraApiService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class TekraServiceConfig {

    @Bean
    public TekraApiService tekraApiService(WebClient webClient, XmlMapper xmlMapper) {
        return new TekraApiService(webClient, xmlMapper);
    }

    @Bean
    public TekraMapper tekraMapper() {
        return new TekraMapper();
    }
}
