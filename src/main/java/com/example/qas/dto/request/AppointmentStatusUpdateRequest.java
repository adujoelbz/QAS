package com.example.qas.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentStatusUpdateRequest {
    @NotNull
    private String status;  // APPROVED, REJECTED, COMPLETED, NO_SHOW, CANCELLED

    private Long doctorId;  // Required for APPROVAL

    private Integer actualWaitTimeMinutes;  // For COMPLETED
}