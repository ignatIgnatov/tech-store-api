package com.techstore.mapper;

import com.techstore.dto.request.ProductCreateRequestDto;
import com.techstore.dto.request.ProductUpdateRequestDto;
import com.techstore.dto.response.ProductResponseDto;
import com.techstore.dto.response.ProductSummaryDto;
import com.techstore.entity.Product;
import com.techstore.entity.ProductImage;
import org.mapstruct.BeanMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", uses = {ManufacturerMapper.class, CategoryMapper.class})
public interface ProductMapper {

    @Mapping(target = "name", expression = "java(getLocalizedName(product, language))")
    @Mapping(target = "description", expression = "java(getLocalizedDescription(product, language))")
    @Mapping(target = "statusName", expression = "java(getLocalizedStatusName(product, language))")
    @Mapping(target = "categories", source = "product.productCategories", qualifiedByName = "mapProductCategoriesToCategories")
    @Mapping(target = "parameters", source = "product.productParameters", qualifiedByName = "mapProductParametersToParameters")
    @Mapping(target = "images", source = "product.productImages", qualifiedByName = "mapProductImagesToImages")
    @Mapping(target = "documents", source = "product.productDocuments", qualifiedByName = "mapProductDocumentsToDocuments")
    @Mapping(target = "flags", source = "product.productFlags", qualifiedByName = "mapProductFlagsToFlags")
    @Mapping(target = "isFavorite", ignore = true)
    ProductResponseDto toResponseDto(Product product, @Context String language);


    @Mapping(target = "name", expression = "java(getLocalizedName(product, language))")
    @Mapping(target = "manufacturer", expression = "java(product.getManufacturer().getName())")
    @Mapping(target = "statusName", expression = "java(getLocalizedStatusName(product, language))")
    @Mapping(target = "primaryImageUrl", expression = "java(getPrimaryImageUrl(product))")
    @Mapping(target = "isFavorite", ignore = true) // Set in service layer
    ProductSummaryDto toSummaryDto(Product product, @Context String language);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "externalId", ignore = true)
    @Mapping(target = "workflowId", ignore = true)
    @Mapping(target = "manufacturer", ignore = true) // Set in service layer
    @Mapping(target = "status", constant = "AVAILABLE")
    @Mapping(target = "pricePartner", ignore = true)
    @Mapping(target = "pricePromo", ignore = true)
    @Mapping(target = "priceClientPromo", ignore = true)
    @Mapping(target = "finalPrice", ignore = true) // Calculated in entity
    @Mapping(target = "productCategories", ignore = true)
    @Mapping(target = "productParameters", ignore = true)
    @Mapping(target = "productImages", ignore = true)
    @Mapping(target = "productDocuments", ignore = true)
    @Mapping(target = "productFlags", ignore = true)
    @Mapping(target = "favorites", ignore = true)
    @Mapping(target = "cartItems", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Product toEntity(ProductCreateRequestDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "externalId", ignore = true)
    @Mapping(target = "workflowId", ignore = true)
    @Mapping(target = "referenceNumber", ignore = true)
    @Mapping(target = "manufacturer", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "pricePartner", ignore = true)
    @Mapping(target = "pricePromo", ignore = true)
    @Mapping(target = "priceClientPromo", ignore = true)
    @Mapping(target = "finalPrice", ignore = true) // Calculated in entity
    @Mapping(target = "productCategories", ignore = true)
    @Mapping(target = "productParameters", ignore = true)
    @Mapping(target = "productImages", ignore = true)
    @Mapping(target = "productDocuments", ignore = true)
    @Mapping(target = "productFlags", ignore = true)
    @Mapping(target = "favorites", ignore = true)
    @Mapping(target = "cartItems", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(@MappingTarget Product product, ProductUpdateRequestDto dto);

    default String getLocalizedName(Product product, String language) {
        return "bg".equals(language) ? product.getNameBg() : product.getNameEn();
    }

    default String getLocalizedDescription(Product product, String language) {
        return "bg".equals(language) ? product.getDescriptionBg() : product.getDescriptionEn();
    }

    default String getLocalizedStatusName(Product product, String language) {
        if (product.getStatus() == null) return null;
        return "bg".equals(language) ? product.getStatus().getNameBg() : product.getStatus().getNameEn();
    }

    default String getPrimaryImageUrl(Product product) {
        return product.getProductImages().stream()
                .filter(ProductImage::getIsPrimary)
                .findFirst()
                .map(ProductImage::getImageUrl)
                .orElse(product.getProductImages().stream()
                        .findFirst()
                        .map(ProductImage::getImageUrl)
                        .orElse(null));
    }

    // Named methods for complex mappings would be implemented here
    @Named("mapProductCategoriesToCategories")
    default java.util.List<com.techstore.dto.response.CategoryResponseDto> mapProductCategoriesToCategories(
            java.util.Set<com.techstore.entity.ProductCategory> productCategories) {
        // Implementation would map ProductCategory entities to CategoryResponseDto
        return java.util.Collections.emptyList(); // Placeholder
    }

    @Named("mapProductParametersToParameters")
    default java.util.List<com.techstore.dto.response.ProductParameterResponseDto> mapProductParametersToParameters(
            java.util.Set<com.techstore.entity.ProductParameter> productParameters) {
        // Implementation would map ProductParameter entities to ProductParameterResponseDto
        return java.util.Collections.emptyList(); // Placeholder
    }

    @Named("mapProductImagesToImages")
    default java.util.List<com.techstore.dto.response.ProductImageResponseDto> mapProductImagesToImages(
            java.util.Set<ProductImage> productImages) {
        // Implementation would map ProductImage entities to ProductImageResponseDto
        return java.util.Collections.emptyList(); // Placeholder
    }

    @Named("mapProductDocumentsToDocuments")
    default java.util.List<com.techstore.dto.response.ProductDocumentResponseDto> mapProductDocumentsToDocuments(
            java.util.Set<com.techstore.entity.ProductDocument> productDocuments) {
        // Implementation would map ProductDocument entities to ProductDocumentResponseDto
        return java.util.Collections.emptyList(); // Placeholder
    }

    @Named("mapProductFlagsToFlags")
    default java.util.List<com.techstore.dto.response.ProductFlagResponseDto> mapProductFlagsToFlags(
            java.util.Set<com.techstore.entity.ProductFlag> productFlags) {
        // Implementation would map ProductFlag entities to ProductFlagResponseDto
        return java.util.Collections.emptyList(); // Placeholder
    }
}