package com.techstore.service;

import com.techstore.dto.ProductComparisonDTO;
import com.techstore.entity.CategorySpecificationTemplate;
import com.techstore.entity.Product;
import com.techstore.entity.ProductSpecification;
import com.techstore.exception.BusinessLogicException;
import com.techstore.repository.CategorySpecificationTemplateRepository;
import com.techstore.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductComparisonService {

    private final ProductRepository productRepository;
    private final CategorySpecificationTemplateRepository templateRepository;

    public List<ProductComparisonDTO> compareProducts(List<Long> productIds) {
        List<Product> products = productRepository.findAllById(productIds);

        if (products.isEmpty()) {
            return Collections.emptyList();
        }

        // Check if all products are from the same category
        Long categoryId = products.get(0).getCategory().getId();
        boolean sameCategory = products.stream()
                .allMatch(p -> p.getCategory().getId().equals(categoryId));

        if (!sameCategory) {
            throw new BusinessLogicException("Can only compare products from the same category");
        }

        // Get comparison specifications for the category
        List<CategorySpecificationTemplate> comparisonSpecs = templateRepository
                .findByCategoryIdOrderBySortOrderAscSpecNameAsc(categoryId)
                .stream()
                .filter(CategorySpecificationTemplate::getShowInComparison)
                .collect(Collectors.toList());

        return products.stream()
                .map(product -> createComparisonDTO(product, comparisonSpecs))
                .collect(Collectors.toList());
    }

    private ProductComparisonDTO createComparisonDTO(Product product,
                                                     List<CategorySpecificationTemplate> comparisonSpecs) {
        Map<String, String> specifications = product.getSpecifications().stream()
                .filter(spec -> comparisonSpecs.stream()
                        .anyMatch(template -> template.getId().equals(spec.getTemplate().getId())))
                .collect(Collectors.toMap(
                        spec -> spec.getTemplate().getSpecName(),
                        ProductSpecification::getFormattedValue
                ));

        return ProductComparisonDTO.builder()
                .id(product.getId())
                .name(product.getNameEn())
                .sku(product.getSku())
                .price(product.getPrice())
                .discountedPrice(product.getDiscountedPrice())
                .imageUrl(product.getImageUrl())
                .brandName(product.getBrand().getName())
                .specifications(specifications)
                .build();
    }
}
