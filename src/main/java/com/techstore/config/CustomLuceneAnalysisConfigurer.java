package com.techstore.config;

import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurationContext;
import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurer;
import org.springframework.stereotype.Component;

@Component
public class CustomLuceneAnalysisConfigurer implements LuceneAnalysisConfigurer {

    @Override
    public void configure(LuceneAnalysisConfigurationContext context) {
        // Define the lowercase normalizer
        context.normalizer("lowercase")
                .custom()
                .tokenFilter("lowercase")
                .tokenFilter("asciifolding");

        // Optional: Define analyzers
        context.analyzer("english")
                .custom()
                .tokenizer("standard")
                .tokenFilter("lowercase")
                .tokenFilter("stop")
                .tokenFilter("porterstem");
    }
}