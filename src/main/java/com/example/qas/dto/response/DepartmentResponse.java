package com.example.qas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentResponse {

    private Long id;
    private Long hospitalId;
    private String name;
    private String specialty;
    private Integer estimatedConsultationDurationMinutes;
}