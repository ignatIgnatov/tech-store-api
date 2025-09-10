package com.techstore.service;

import com.techstore.dto.*;
import com.techstore.entity.Product;
import com.techstore.entity.ProductSpecification;
import com.techstore.entity.Category;
import com.techstore.entity.Brand;
import com.techstore.repository.ProductRepository;
import com.techstore.repository.ProductSpecificationRepository;
import com.techstore.repository.CategoryRepository;
import com.techstore.repository.BrandRepository;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.exception.DuplicateResourceException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional(readOnly = true)
    public List<BrandResponseDTO> getAllBrands() {
        return brandRepository.findByActiveTrueOrderBySortOrderAscNameAsc()
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<BrandResponseDTO> getAllBrands(Pageable pageable) {
        return brandRepository.findByActiveTrue(pageable)
                .map(this::convertToResponseDTO);
    }

    @Transactional(readOnly = true)
    public BrandResponseDTO getBrandById(Long id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + id));
        return convertToResponseDTO(brand);
    }

    @Transactional(readOnly = true)
    public BrandResponseDTO getBrandBySlug(String slug) {
        Brand brand = brandRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with slug: " + slug));
        return convertToResponseDTO(brand);
    }

    @Transactional(readOnly = true)
    public List<BrandResponseDTO> getFeaturedBrands() {
        return brandRepository.findByActiveTrueAndFeaturedTrueOrderBySortOrderAscNameAsc()
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    public BrandResponseDTO createBrand(BrandRequestDTO requestDTO) {
        log.info("Creating new brand with slug: {}", requestDTO.getSlug());

        if (brandRepository.existsBySlug(requestDTO.getSlug())) {
            throw new DuplicateResourceException("Brand with slug '" + requestDTO.getSlug() + "' already exists");
        }

        Brand brand = convertToEntity(requestDTO);
        brand = brandRepository.save(brand);

        log.info("Brand created successfully with id: {}", brand.getId());
        return convertToResponseDTO(brand);
    }

    public BrandResponseDTO updateBrand(Long id, BrandRequestDTO requestDTO) {
        log.info("Updating brand with id: {}", id);

        Brand existingBrand = brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + id));

        if (brandRepository.existsBySlugAndIdNot(requestDTO.getSlug(), id)) {
            throw new DuplicateResourceException("Brand with slug '" + requestDTO.getSlug() + "' already exists");
        }

        updateBrandFromDTO(existingBrand, requestDTO);
        Brand updatedBrand = brandRepository.save(existingBrand);

        log.info("Brand updated successfully with id: {}", id);
        return convertToResponseDTO(updatedBrand);
    }

    public void deleteBrand(Long id) {
        log.info("Deleting brand with id: {}", id);

        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + id));

        brand.setActive(false);
        brandRepository.save(brand);

        log.info("Brand soft deleted successfully with id: {}", id);
    }

    private Brand convertToEntity(BrandRequestDTO dto) {
        Brand brand = new Brand();
        brand.setName(dto.getName());
        brand.setSlug(dto.getSlug());
        brand.setDescription(dto.getDescription());
        brand.setLogoUrl(dto.getLogoUrl());
        brand.setWebsiteUrl(dto.getWebsiteUrl());
        brand.setCountry(dto.getCountry());
        brand.setActive(dto.getActive());
        brand.setFeatured(dto.getFeatured());
        brand.setSortOrder(dto.getSortOrder());
        return brand;
    }

    private void updateBrandFromDTO(Brand brand, BrandRequestDTO dto) {
        brand.setName(dto.getName());
        brand.setSlug(dto.getSlug());
        brand.setDescription(dto.getDescription());
        brand.setLogoUrl(dto.getLogoUrl());
        brand.setWebsiteUrl(dto.getWebsiteUrl());
        brand.setCountry(dto.getCountry());
        brand.setActive(dto.getActive());
        brand.setFeatured(dto.getFeatured());
        brand.setSortOrder(dto.getSortOrder());
    }

    private BrandResponseDTO convertToResponseDTO(Brand brand) {
        return BrandResponseDTO.builder()
                .id(brand.getId())
                .name(brand.getName())
                .slug(brand.getSlug())
                .description(brand.getDescription())
                .logoUrl(brand.getLogoUrl())
                .websiteUrl(brand.getWebsiteUrl())
                .country(brand.getCountry())
                .active(brand.getActive())
                .featured(brand.getFeatured())
                .sortOrder(brand.getSortOrder())
                .productCount(brand.getProductCount())
                .activeProductCount(brand.getActiveProductCount())
                .createdAt(brand.getCreatedAt())
                .updatedAt(brand.getUpdatedAt())
                .build();
    }
}
