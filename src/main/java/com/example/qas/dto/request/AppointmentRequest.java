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
public class AppointmentRequest {

    @NotNull
    private Long departmentId;

    @NotNull
    @FutureOrPresent
    private LocalDate requestedDate;

    @NotNull
    private LocalTime requestedTime;

    private String reason;

    private Boolean emergencyFlag;
}