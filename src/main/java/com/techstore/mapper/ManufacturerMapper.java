package com.techstore.mapper;

import com.techstore.dto.response.ManufacturerResponseDto;
import com.techstore.entity.Manufacturer;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ManufacturerMapper {

    ManufacturerResponseDto toResponseDto(Manufacturer manufacturer);
}