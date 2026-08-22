package com.example.qas.dto.response;

import com.example.qas.models.enums.AppointmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentResponse {

    private Long id;
    private Long patientId;
    private Long departmentId;
    private Long doctorId;
    private LocalDate requestedDate;
    private LocalTime requestedTime;
    private AppointmentStatus status;
    private String reason;
    private Boolean emergencyFlag;
    private Integer queuePosition;
    private Integer estimatedWaitTimeMinutes;
    private Integer actualWaitTimeMinutes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime cancelledAt;
    private OffsetDateTime completedAt;
}