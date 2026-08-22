package com.example.qas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStats {

    private Long totalPatients;
    private Long totalDoctors;
    private Long totalAppointmentsToday;
    private Double averageWaitTimeMinutes;
    private Double noShowRate;
    private Map<String, Double> aiAccuracy;  // e.g., "noShowPrediction": 0.88, "waitTimePredictionMAE": 4.5
}