package com.example.qas.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentCreateRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String specialty;

    @NotNull
    private Integer estimatedConsultationDurationMinutes;
}