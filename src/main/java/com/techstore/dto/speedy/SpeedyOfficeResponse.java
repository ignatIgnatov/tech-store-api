package com.techstore.dto.speedy;

import lombok.Data;

import java.util.List;

@Data
public class SpeedyOfficeResponse {
    private List<SpeedyOffice> offices;
    private SpeedyError error;
}
