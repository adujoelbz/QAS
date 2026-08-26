package com.example.qas.controllers;

import com.example.qas.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {
    private final UserRepository userRepository;
    private final WebClient webClient;

    @Value("${ai.service.url:http://localhost:5000}")
    private String aiServiceUrl;

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/api/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        boolean database = false;
        boolean ai = false;
        try {
            userRepository.count();
            database = true;
        } catch (Exception ignored) { }
        try {
            webClient.get().uri(aiServiceUrl + "/health").retrieve().toBodilessEntity()
                    .timeout(Duration.ofSeconds(2)).block();
            ai = true;
        } catch (Exception ignored) { }
        Map<String, Object> result = Map.of("status", database ? "UP" : "DOWN", "database", database, "aiService", ai);
        return ResponseEntity.status(database ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(result);
    }
}
