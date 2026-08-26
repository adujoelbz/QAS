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
public class SlotRecommendationResponse {
    private List<RecommendedSlot> slots;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendedSlot {
        private LocalDate date;
        private LocalTime time;
        private Integer estimatedWaitMinutes;
        private Double confidence;
        private String reason;
    }
}