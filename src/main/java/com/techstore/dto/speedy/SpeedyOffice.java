package com.techstore.dto.speedy;

import lombok.Data;

import java.util.List;

@Data
public class SpeedyOffice {
    private Long id;
    private String name;
    private SpeedySite site;
    private String address;
    private WorkingTime workingTime;

    @Data
    public static class WorkingTime {
        private String fullTime;
        private List<Schedule> schedule;
    }

    @Data
    public static class Schedule {
        private String dayOfWeek;
        private String workingTimeFrom;
        private String workingTimeTo;
    }
}
