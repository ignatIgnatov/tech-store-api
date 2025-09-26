package com.techstore.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class FavoriteRequestDto {

    @NotNull
    @Positive
    private Long productId;
}