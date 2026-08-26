package com.example.qas.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoShowPredictionResponse {
    private Double probability;
    private String riskLevel; // LOW, MEDIUM, HIGH
    private String recommendation;
}