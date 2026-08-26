package com.example.qas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorProfileResponse {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String specialty;
    private Long hospitalId;
    private String hospitalName;
    private Integer consultationDurationMinutes;
    private Map<String, Object> availableDays;
}