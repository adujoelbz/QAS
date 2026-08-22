package com.example.qas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionReport {

    private Long id;
    private Long appointmentId;
    private String predictionType;  // "NO_SHOW", "WAIT_TIME", "SLOT_RECOMMENDATION"
    private Map<String, Object> inputFeatures;
    private Map<String, Object> predictionResult;
    private Map<String, Object> actualOutcome;
    private String modelVersion;
    private OffsetDateTime createdAt;
}