package com.example.qas.dto.response;

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
public class SlotRecommendation {

    private LocalDate date;
    private LocalTime time;
    private Integer estimatedWaitMinutes;
    private Double confidence;
    private String reason;
}