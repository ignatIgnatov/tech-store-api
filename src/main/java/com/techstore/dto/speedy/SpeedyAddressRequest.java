package com.techstore.dto.speedy;

import lombok.Data;

@Data
public class SpeedyAddressRequest {
    private Long siteId;
    private String searchString;
}
