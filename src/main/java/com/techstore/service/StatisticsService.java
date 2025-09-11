package com.techstore.service;

import com.techstore.dto.response.CategoryStatisticsDto;
import com.techstore.dto.response.ProductStatisticsDto;
import com.techstore.repository.CategoryRepository;
import com.techstore.repository.ManufacturerRepository;
import com.techstore.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatisticsService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ManufacturerRepository manufacturerRepository;

    @Cacheable(value = "statistics", key = "'product_stats'")
    public ProductStatisticsDto getProductStatistics() {
        ProductStatisticsDto stats = new ProductStatisticsDto();

        stats.setTotalProducts(productRepository.count());
        stats.setAvailableProducts(productRepository.countAvailableProducts());
        stats.setCategoriesWithProducts((long) categoryRepository.findCategoriesWithAvailableProducts().size());
        stats.setTotalManufacturers((long) manufacturerRepository.findManufacturersWithAvailableProducts().size());

        stats.setMinPrice(productRepository.findMinPrice().orElse(BigDecimal.ZERO));
        stats.setMaxPrice(productRepository.findMaxPrice().orElse(BigDecimal.ZERO));

        // Calculate average price (would need a custom query)
        stats.setAveragePrice(BigDecimal.ZERO); // Placeholder

        return stats;
    }

    @Cacheable(value = "statistics", key = "'category_stats_' + #language")
    public List<CategoryStatisticsDto> getCategoryStatistics(String language) {
        // Implementation would involve complex queries to calculate statistics per category
        // This is a placeholder implementation
        return List.of();
    }
}