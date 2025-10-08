package com.techstore.dto.speedy;

import lombok.Data;

@Data
public class SpeedyAuthenticationResponse {
    private String accessToken;
    private Long expiresIn;
}
