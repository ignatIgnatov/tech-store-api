package com.techstore.mapper;

import com.techstore.dto.response.ProductSummaryDto;
import com.techstore.entity.Product;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring", uses = {ManufacturerMapper.class})
public interface ProductMapper {

    @Mapping(target = "manufacturer", expression = "java(product.getManufacturer().getName())")
    @Mapping(target = "statusName", expression = "java(getLocalizedStatusName(product, language))")
    @Mapping(target = "isFavorite", ignore = true)
    ProductSummaryDto toSummaryDto(Product product, @Context String language);

    default String getLocalizedStatusName(Product product, String language) {
        if (product.getStatus() == null) return null;
        return "bg".equals(language) ? product.getStatus().getNameBg() : product.getStatus().getNameEn();
    }

    @Named("mapProductParametersToParameters")
    default java.util.List<com.techstore.dto.response.ProductParameterResponseDto> mapProductParametersToParameters(
            java.util.Set<com.techstore.entity.ProductParameter> productParameters) {
        return java.util.Collections.emptyList();
    }

    @Named("mapProductFlagsToFlags")
    default java.util.List<com.techstore.dto.response.ProductFlagResponseDto> mapProductFlagsToFlags(
            java.util.Set<com.techstore.entity.ProductFlag> productFlags) {
        return java.util.Collections.emptyList();
    }
}