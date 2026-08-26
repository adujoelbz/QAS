package com.example.qas.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitTimePredictionResponse {
    private Integer predictedWaitMinutes;
    private Integer confidence;
}