package com.example.qas.services.ai;

import com.example.qas.dto.ai.NoShowPredictionRequest;
import com.example.qas.dto.ai.NoShowPredictionResponse;
import com.example.qas.dto.ai.SlotRecommendationRequest;
import com.example.qas.dto.ai.SlotRecommendationResponse;
import com.example.qas.dto.ai.WaitTimePredictionRequest;
import com.example.qas.dto.ai.WaitTimePredictionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIServiceClient {

    private final WebClient webClient;

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    @Value("${ai.service.timeout:5000}")
    private int timeout;

    // === Slot Recommendations ===

    public SlotRecommendationResponse recommendSlots(SlotRecommendationRequest request) {
        try {
            return webClient.post()
                    .uri(aiServiceUrl + "/recommend-slots")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(SlotRecommendationResponse.class)
                    .timeout(Duration.ofMillis(timeout))
                    .retryWhen(Retry.max(1))
                    .block();
        } catch (Exception e) {
            log.error("AI slot recommendation failed: {}", e.getMessage());
            return null; // fallback will handle
        }
    }

    // === No-Show Prediction ===

    public NoShowPredictionResponse predictNoShow(NoShowPredictionRequest request) {
        try {
            return webClient.post()
                    .uri(aiServiceUrl + "/predict-no-show")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(NoShowPredictionResponse.class)
                    .timeout(Duration.ofMillis(timeout))
                    .retryWhen(Retry.max(1))
                    .block();
        } catch (Exception e) {
            log.error("AI no-show prediction failed: {}", e.getMessage());
            return null;
        }
    }

    // === Wait-Time Prediction ===

    public WaitTimePredictionResponse predictWaitTime(WaitTimePredictionRequest request) {
        try {
            return webClient.post()
                    .uri(aiServiceUrl + "/predict-wait-time")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(WaitTimePredictionResponse.class)
                    .timeout(Duration.ofMillis(timeout))
                    .retryWhen(Retry.max(1))
                    .block();
        } catch (Exception e) {
            log.error("AI wait-time prediction failed: {}", e.getMessage());
            return null;
        }
    }
}