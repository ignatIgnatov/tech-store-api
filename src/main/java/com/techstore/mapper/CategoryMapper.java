package com.techstore.mapper;

import com.techstore.dto.response.CategoryResponseDto;
import com.techstore.entity.Category;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    @Mapping(target = "name", expression = "java(getLocalizedName(category, language))")
    @Mapping(target = "parentId", source = "category.parent.id")
    @Mapping(target = "parentName", expression = "java(getParentName(category, language))")
    CategoryResponseDto toResponseDto(Category category, @Context String language);

    default String getLocalizedName(Category category, String language) {
        return "bg".equals(language) ? category.getNameBg() : category.getNameEn();
    }

    default String getParentName(Category category, String language) {
        if (category.getParent() == null) return null;
        return "bg".equals(language) ? category.getParent().getNameBg() : category.getParent().getNameEn();
    }
}