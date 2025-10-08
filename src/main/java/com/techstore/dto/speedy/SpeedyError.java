package com.techstore.dto.speedy;

import lombok.Data;

@Data
class SpeedyError {
    private String id;
    private String message;
    private String component;
    private String context;
}