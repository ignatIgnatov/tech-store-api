package com.techstore.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ManufacturerResponseDto {
    private Long id;
    private Long externalId;
    private String name;
    private String informationName;
    private String informationEmail;
    private String informationAddress;
    private String euRepresentativeName;
    private String euRepresentativeEmail;
    private String euRepresentativeAddress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
