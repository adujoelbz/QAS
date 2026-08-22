package com.example.qas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueAnalyticsResponse {

    private String departmentName;
    private Integer currentQueueLength;
    private Double averageWaitMinutes;
    private String peakTime;       // e.g., "10:00-11:00"
    private Double slotUtilization;
}