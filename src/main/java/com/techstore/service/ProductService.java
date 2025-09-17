package com.techstore.service;

import com.techstore.dto.ProductResponseDTO;
import com.techstore.dto.external.ProductRequestDto;
import com.techstore.dto.external.ImageDto;
import com.techstore.dto.request.ParameterValueRequestDto;
import com.techstore.dto.response.CategorySummaryDTO;
import com.techstore.dto.response.ManufacturerSummaryDto;
import com.techstore.entity.Category;
import com.techstore.entity.Manufacturer;
import com.techstore.entity.Product;
import com.techstore.entity.ProductParameter;
import com.techstore.enums.ProductStatus;
import com.techstore.exception.DuplicateResourceException;
import com.techstore.exception.ResourceNotFoundException;
import com.techstore.repository.CategoryRepository;
import com.techstore.repository.ManufacturerRepository;
import com.techstore.repository.ParameterOptionRepository;
import com.techstore.repository.ParameterRepository;
import com.techstore.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final ParameterRepository parameterRepository;
    private final ParameterOptionRepository parameterOptionRepository;

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getAllProducts(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable)
                .map(this::convertToResponseDTO);
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return convertToResponseDTO(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getProductsByCategory(Long categoryId, Pageable pageable) {
        return productRepository.findByActiveTrueAndCategoryId(categoryId, pageable)
                .map(this::convertToResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getProductsByBrand(Long brandId, Pageable pageable) {
        return productRepository.findByActiveTrueAndManufacturerId(brandId, pageable)
                .map(this::convertToResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getFeaturedProducts(Pageable pageable) {
        return productRepository.findByActiveTrueAndFeaturedTrue(pageable)
                .map(this::convertToResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getProductsOnSale(Pageable pageable) {
        return productRepository.findProductsOnSale(pageable)
                .map(this::convertToResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> searchProducts(String query, Pageable pageable) {
        return productRepository.searchProducts(query, pageable)
                .map(this::convertToResponseDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> filterProducts(Long categoryId, Long brandId,
                                                   BigDecimal minPrice, BigDecimal maxPrice,
                                                   ProductStatus status, Boolean onSale, String query,
                                                   Pageable pageable) {
        return productRepository.findProductsWithFilters(categoryId, brandId, minPrice, maxPrice,
                        status, onSale, query, pageable)
                .map(this::convertToResponseDTO);
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getRelatedProducts(Long productId, int limit) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        Pageable pageable = Pageable.ofSize(limit);
        return productRepository.findRelatedProducts(productId, product.getCategory().getId(),
                        product.getManufacturer().getId(), pageable)
                .stream()
                .map(this::convertToResponseDTO)
                .toList();
    }

    @Transactional
    public ProductResponseDTO createProduct(ProductRequestDto dto) {
        if (productRepository.existsByReferenceNumberIgnoreCase(dto.getReferenceNumber())) {
            throw new DuplicateResourceException("Product with already exists with ref. number " + dto.getReferenceNumber());
        }
        Product product = new Product();

        updateProductFieldsFromExternal(product, dto);

        productRepository.save(product);

        return convertToResponseDTO(product);

    }

    private void updateProductFieldsFromExternal(Product product, ProductRequestDto dto) {
        setNamesToProduct(product, dto);
        setDescriptionToProduct(product, dto);
        product.setReferenceNumber(dto.getReferenceNumber());
        product.setModel(dto.getModel());
        product.setBarcode(dto.getBarcode());
        product.setExternalId(dto.getId());
        product.setWorkflowId(dto.getIdWF());

        setCategoryToProduct(product, dto);
        setManufacturer(product, dto);

        product.setStatus(ProductStatus.fromCode(dto.getStatus()));
        product.setPriceClient(dto.getPriceClient());
        product.setPricePartner(dto.getPricePartner());
        product.setPricePromo(dto.getPricePromo());
        product.setPriceClientPromo(dto.getPriceClientPromo());
        product.setMarkupPercentage(dto.getMarkupPercentage());
        product.calculateFinalPrice();

        setImagesToProduct(product, dto);
        setParametersToProduct(product, dto);
    }

    private void setManufacturer(Product product, ProductRequestDto dto) {
        Manufacturer manufacturer = manufacturerRepository.findById(dto.getManufacturerId())
                .orElseThrow(() -> new ResourceNotFoundException("Manufacturer not found: " + dto.getManufacturerId()));
        product.setManufacturer(manufacturer);
    }

    private static void setImagesToProduct(Product product, ProductRequestDto extProduct) {
        if (extProduct.getImages() != null && !extProduct.getImages().isEmpty()) {
            product.setPrimaryImageUrl(extProduct.getImages().get(0).getHref());
            product.setAdditionalImages(
                    extProduct.getImages().stream().skip(1).map(ImageDto::getHref).toList()
            );
        }
    }

    private void setCategoryToProduct(Product product, ProductRequestDto extProduct) {
        categoryRepository.findByExternalId(extProduct.getCategories().get(0).getId())
                .ifPresent(product::setCategory);
    }

    private static void setDescriptionToProduct(Product product, ProductRequestDto extProduct) {
        if (extProduct.getDescription() != null) {
            extProduct.getDescription().forEach(desc -> {
                switch (desc.getLanguageCode()) {
                    case "bg" -> product.setDescriptionBg(desc.getText());
                    case "en" -> product.setDescriptionEn(desc.getText());
                }
            });
        }
    }

    private static void setNamesToProduct(Product product, ProductRequestDto extProduct) {
        if (extProduct.getName() != null) {
            extProduct.getName().forEach(name -> {
                switch (name.getLanguageCode()) {
                    case "bg" -> product.setNameBg(name.getText());
                    case "en" -> product.setNameEn(name.getText());
                }
            });
        }
    }

    public ProductResponseDTO updateProduct(Long id, ProductRequestDto requestDTO) {
        Product product = productRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Product not found with id " + id)
        );

        updateProductFieldsFromExternal(product, requestDTO);

        productRepository.save(product);

        return null;
    }

    public void deleteProduct(Long id) {
        log.info("Deleting product with id: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        product.setActive(false);
        productRepository.save(product);

        log.info("Product soft deleted successfully with id: {}", id);
    }

    public void permanentDeleteProduct(Long id) {
        log.info("Permanently deleting product with id: {}", id);

        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }

        productRepository.deleteById(id);
        log.info("Product permanently deleted successfully with id: {}", id);
    }

    private ProductResponseDTO convertToResponseDTO(Product product) {
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
        dto.setSpecifications(product.getProductParameters().stream().toList());

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

    private void setParametersToProduct(Product product, ProductRequestDto extProduct) {
        if (extProduct.getParameters() != null && product.getCategory() != null) {
            product.getProductParameters().clear();

            for (ParameterValueRequestDto paramValue : extProduct.getParameters()) {
                parameterRepository.findByExternalIdAndCategoryId(paramValue.getParameterId(), product.getCategory().getId())
                        .ifPresent(parameter ->
                                parameterOptionRepository.findByExternalIdAndParameterId(paramValue.getOptionId(), parameter.getId())
                                        .ifPresent(option -> {
                                            ProductParameter pp = new ProductParameter();
                                            pp.setProduct(product);
                                            pp.setParameter(parameter);
                                            pp.setParameterOption(option);
                                            product.getProductParameters().add(pp);
                                        })
                        );
            }
        }
    }
}