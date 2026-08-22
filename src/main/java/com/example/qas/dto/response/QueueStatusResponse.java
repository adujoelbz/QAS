package com.example.qas.dto.response;

import com.example.qas.models.enums.AppointmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueStatusResponse {

    private Long appointmentId;
    private Integer queuePosition;
    private Integer patientsAhead;
    private Integer estimatedWaitMinutes;
    private AppointmentStatus status;
}