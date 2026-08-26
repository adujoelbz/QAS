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
public class DashboardStatsResponse {
    private Long totalPatients;
    private Long totalDoctors;
    private Long totalAdmins;
    private Long totalAppointmentsToday;
    private Long totalAppointmentsThisWeek;
    private Long totalAppointmentsThisMonth;
    private Double averageWaitTimeMinutes;
    private Double noShowRate;
    private Map<String, Double> aiAccuracy; // e.g., "noShowPrediction": 0.88, "waitTimePredictionMAE": 4.5
}