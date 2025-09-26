package com.techstore.dto.response;

import lombok.Data;

@Data
public class FavoriteCountResponseDto {

    private Long count;
    private Integer maxLimit;
    private Boolean limitReached;

    public FavoriteCountResponseDto(Long count, Integer maxLimit) {
        this.count = count;
        this.maxLimit = maxLimit;
        this.limitReached = count >= maxLimit;
    }
}