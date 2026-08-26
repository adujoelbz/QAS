package com.example.qas.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotRecommendationRequest {
    private Long departmentId;
    private LocalDate preferredDate;
    private LocalTime preferredTime;
    private Integer slotDurationMinutes;
    private Integer queueLength;
}