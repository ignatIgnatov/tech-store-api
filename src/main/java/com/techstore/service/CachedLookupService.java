package com.techstore.service;

import com.techstore.entity.Category;
import com.techstore.entity.Manufacturer;
import com.techstore.entity.Parameter;
import com.techstore.entity.ParameterOption;
import com.techstore.entity.Product;
import com.techstore.repository.CategoryRepository;
import com.techstore.repository.ManufacturerRepository;
import com.techstore.repository.ParameterOptionRepository;
import com.techstore.repository.ParameterRepository;
import com.techstore.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CachedLookupService {

    private final ParameterRepository parameterRepository;
    private final ParameterOptionRepository parameterOptionRepository;
    private final CategoryRepository categoryRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final ProductRepository productRepository;


    @Cacheable(value = "parameters", key = "#externalId + '-' + #categoryId")
    public Optional<Parameter> getParameter(Long externalId, Long categoryId) {
        return parameterRepository.findByExternalIdAndCategoryId(externalId, categoryId);
    }

    @Cacheable(value = "parameterOptions", key = "#externalId + '-' + #parameterId")
    public Optional<ParameterOption> getParameterOption(Long externalId, Long parameterId) {
        return parameterOptionRepository.findByExternalIdAndParameterId(externalId, parameterId);
    }

    @Cacheable(value = "categoriesByExternalId")
    public Map<Long, Category> getAllCategoriesMap() {
        return categoryRepository.findAll()
                .stream()
                .collect(Collectors.toMap(Category::getExternalId, c -> c));
    }

    @Cacheable(value = "manufacturersByExternalId")
    public Map<Long, Manufacturer> getAllManufacturersMap() {
        return manufacturerRepository.findAll()
                .stream()
                .collect(Collectors.toMap(Manufacturer::getExternalId, m -> m));
    }

    @Cacheable(value = "parametersByCategory")
    public Map<String, Parameter> getParametersByCategory(Category category) {
        return parameterRepository.findAllByCategoryId(category.getId())
                .stream()
                .collect(Collectors.toMap(p -> p.getExternalId().toString(), p -> p));
    }

    @Cacheable(value = "productsByCategory")
    public Map<Long, Product> getProductsByCategory(Category category) {
        return productRepository.findAllByCategoryId(category.getId())
                .stream()
                .collect(Collectors.toMap(Product::getExternalId, p -> p));
    }
}
