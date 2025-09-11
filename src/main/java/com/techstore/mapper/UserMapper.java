package com.techstore.mapper;

import com.techstore.dto.request.UserRegistrationRequestDto;
import com.techstore.dto.response.UserResponseDto;
import com.techstore.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponseDto toResponseDto(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "favorites", ignore = true)
    @Mapping(target = "cartItems", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User toEntity(UserRegistrationRequestDto dto);
}