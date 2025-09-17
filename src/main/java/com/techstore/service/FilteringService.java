package com.techstore.service;

import com.techstore.dto.filter.AdvancedFilterRequestDTO;
import com.techstore.dto.ProductResponseDTO;
import com.techstore.dto.response.CategorySummaryDTO;
import com.techstore.dto.response.ManufacturerSummaryDto;
import com.techstore.dto.response.ProductParameterResponseDto;
import com.techstore.entity.Category;
import com.techstore.entity.Manufacturer;
import com.techstore.entity.Product;
import com.techstore.entity.ProductParameter;
import com.techstore.mapper.ParameterMapper;
import com.techstore.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FilteringService {

    private final ProductRepository productRepository;
    private final ParameterMapper parameterMapper;

    /**
     * Advanced product filtering with specification-based filters
     */
    public Page<ProductResponseDTO> filterProductsAdvanced(AdvancedFilterRequestDTO filterRequest, Pageable pageable, String lang) {
        // Build dynamic query based on filters
        List<Product> filteredProducts = buildFilteredProductQuery(filterRequest);

        // Convert to pageable result
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filteredProducts.size());

        List<Product> pageContent = filteredProducts.subList(start, end);

        Page<Product> productPage = new PageImpl<>(pageContent, pageable, filteredProducts.size());

        return productPage.map(p -> convertToResponseDTO(p, lang));
    }

    private List<Product> buildFilteredProductQuery(AdvancedFilterRequestDTO filterRequest) {
        // Start with base query
        Set<Product> candidates = new HashSet<>(productRepository.findByActiveTrueOrderByNameEnAsc());

        // Apply category filter
        if (filterRequest.getCategoryId() != null) {
            candidates.retainAll(productRepository.findByActiveTrueAndCategoryId(
                    filterRequest.getCategoryId(), Pageable.unpaged()).getContent());
        }

        // Apply brand filter
        if (filterRequest.getManufacturerId() != null) {
            candidates.retainAll(productRepository.findByActiveTrueAndManufacturerId(
                    filterRequest.getManufacturerId(), Pageable.unpaged()).getContent());
        }

        // Apply price range filter
        if (filterRequest.getMinPrice() != null || filterRequest.getMaxPrice() != null) {
            BigDecimal min = filterRequest.getMinPrice() != null ? filterRequest.getMinPrice() : BigDecimal.ZERO;
            BigDecimal max = filterRequest.getMaxPrice() != null ? filterRequest.getMaxPrice() : new BigDecimal("999999");
            candidates.retainAll(productRepository.findByPriceRange(min, max, Pageable.unpaged()).getContent());
        }

        // Apply text search
        if (filterRequest.getSearchQuery() != null && !filterRequest.getSearchQuery().trim().isEmpty()) {
            candidates.retainAll(productRepository.searchProducts(
                    filterRequest.getSearchQuery(), Pageable.unpaged()).getContent());
        }

        // Apply availability filter
        if (filterRequest.getInStockOnly() != null && filterRequest.getInStockOnly()) {
            candidates.removeIf(product -> product.getStatus().getCode() == 0);
        }

        // Apply sale filter
        if (filterRequest.getOnSaleOnly() != null && filterRequest.getOnSaleOnly()) {
            candidates.removeIf(product -> !product.isOnSale());
        }

        return new ArrayList<>(candidates);
    }

    private ProductResponseDTO convertToResponseDTO(Product product, String lang) {
        ProductResponseDTO dto = new ProductResponseDTO();

        dto.setId(product.getId());
        dto.setNameEn(product.getNameEn());
        dto.setNameBg(product.getNameBg());
        dto.setDescriptionEn(product.getDescriptionEn());
        dto.setDescriptionBg(product.getDescriptionBg());

        dto.setReferenceNumber(product.getReferenceNumber());
        dto.setModel(product.getModel());
        dto.setBarcode(product.getBarcode());

        dto.setPriceClient(product.getPriceClient());
        dto.setPricePartner(product.getPricePartner());
        dto.setPricePromo(product.getPricePromo());
        dto.setPriceClientPromo(product.getPriceClientPromo());
        dto.setMarkupPercentage(product.getMarkupPercentage());
        dto.setFinalPrice(product.getFinalPrice());
        dto.setDiscount(product.getDiscount());

        dto.setActive(product.getActive());
        dto.setFeatured(product.getFeatured());
        dto.setShow(product.getShow());

        dto.setPrimaryImageUrl(product.getPrimaryImageUrl());
        dto.setAdditionalImages(product.getAdditionalImages());

        dto.setWarranty(product.getWarranty());
        dto.setWeight(product.getWeight());
        dto.setSpecifications(product.getProductParameters().stream().map(p -> convertToProductParameterResponse(p, lang)).toList());

        if (product.getCategory() != null) {
            dto.setCategory(convertToCategorySummary(product.getCategory()));
        }

        if (product.getManufacturer() != null) {
            dto.setManufacturer(convertToManufacturerSummary(product.getManufacturer()));
        }

        dto.setCreatedAt(product.getCreatedAt());
        dto.setUpdatedAt(product.getUpdatedAt());
        dto.setOnSale(product.isOnSale());
        dto.setStatus(product.getStatus() != null ? product.getStatus().getCode() : 0);
        dto.setWorkflowId(product.getWorkflowId());

        return dto;
    }

    private CategorySummaryDTO convertToCategorySummary(Category category) {
        return CategorySummaryDTO.builder()
                .id(category.getId())
                .nameEn(category.getNameEn())
                .nameBg(category.getNameBg())
                .slug(category.getSlug())
                .show(category.getShow())
                .build();
    }

    private ManufacturerSummaryDto convertToManufacturerSummary(Manufacturer manufacturer) {
        return ManufacturerSummaryDto.builder()
                .id(manufacturer.getId())
                .name(manufacturer.getName())
                .build();
    }

    private ProductParameterResponseDto convertToProductParameterResponse(ProductParameter productParameter, String lang) {
        return ProductParameterResponseDto.builder()
                .parameterId(productParameter.getParameter().getId())
                .parameterNameEn(productParameter.getParameter().getNameEn())
                .parameterNameBg(productParameter.getParameter().getNameBg())
                .options(productParameter.getParameter().getOptions().stream()
                        .map(p -> parameterMapper.toOptionResponseDto(p, lang)).toList())
                .build();
    }
}
