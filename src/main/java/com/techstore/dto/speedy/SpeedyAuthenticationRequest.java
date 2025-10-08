package com.techstore.dto.speedy;

import lombok.Data;

@Data
public class SpeedyAuthenticationRequest {
    private String userName;
    private String password;
}
