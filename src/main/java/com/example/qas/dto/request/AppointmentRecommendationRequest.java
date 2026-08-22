package com.example.qas.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
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
public class AppointmentRecommendationRequest {

    @NotNull
    private Long departmentId;

    @NotNull
    @FutureOrPresent
    private LocalDate preferredDate;

    private LocalTime preferredTime;
}