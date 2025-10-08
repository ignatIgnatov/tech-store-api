package com.techstore.dto.speedy;

import lombok.Data;

@Data
public class SpeedySite {
    private Long id;
    private String type;
    private String name;
    private String municipality;
    private String region;
    private PostCode postCode;

    @Data
    public static class PostCode {
        private String id;
        private String name;
    }
}
