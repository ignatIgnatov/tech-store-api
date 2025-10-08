package com.techstore.dto.speedy;

import lombok.Data;

import java.util.List;

@Data
public class SpeedySiteResponse {
    private List<SpeedySite> sites;
    private SpeedyError error;
}
