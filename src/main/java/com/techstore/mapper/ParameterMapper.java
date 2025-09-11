package com.techstore.mapper;

import com.techstore.dto.response.ParameterOptionResponseDto;
import com.techstore.dto.response.ParameterResponseDto;
import com.techstore.entity.Parameter;
import com.techstore.entity.ParameterOption;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ParameterMapper {

    @Mapping(target = "name", expression = "java(getLocalizedName(parameter, language))")
    @Mapping(target = "categoryId", source = "parameter.category.externalId")
    @Mapping(target = "categoryName", expression = "java(getCategoryName(parameter, language))")
    @Mapping(target = "options", expression = "java(mapOptions(parameter, language))")
    ParameterResponseDto toResponseDto(Parameter parameter, @Context String language);

    @Mapping(target = "name", expression = "java(getLocalizedOptionName(option, language))")
    @Mapping(target = "parameterId", source = "parameter.externalId")
    @Mapping(target = "parameterName", expression = "java(getParameterName(option, language))")
    ParameterOptionResponseDto toOptionResponseDto(ParameterOption option, @Context String language);

    default String getLocalizedName(Parameter parameter, String language) {
        return "bg".equals(language) ? parameter.getNameBg() : parameter.getNameEn();
    }

    default String getLocalizedOptionName(ParameterOption option, String language) {
        return "bg".equals(language) ? option.getNameBg() : option.getNameEn();
    }

    default String getCategoryName(Parameter parameter, String language) {
        if (parameter.getCategory() == null) return null;
        return "bg".equals(language) ? parameter.getCategory().getNameBg() : parameter.getCategory().getNameEn();
    }

    default String getParameterName(ParameterOption option, String language) {
        if (option.getParameter() == null) return null;
        return "bg".equals(language) ? option.getParameter().getNameBg() : option.getParameter().getNameEn();
    }

    default java.util.List<ParameterOptionResponseDto> mapOptions(Parameter parameter, String language) {
        return parameter.getOptions().stream()
                .sorted((o1, o2) -> Integer.compare(o1.getOrder(), o2.getOrder()))
                .map(option -> toOptionResponseDto(option, language))
                .collect(java.util.stream.Collectors.toList());
    }
}