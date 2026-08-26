package com.example.qas.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitTimePredictionRequest {
    private Long departmentId;
    private LocalDate date;
    private LocalTime time;
    private Integer queuePosition;
    private Integer patientsAhead;
    private Integer averageConsultationDuration;
    private Integer currentQueueLength;
    private List<Long> previousAppointmentDurations; // optional
}