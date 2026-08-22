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
public class AdminDoctorResponse {

    private Long id;
    private Long userId;
    private String firstName;
    private String lastName;
    private String specialty;
    private Long hospitalId;
    private Integer consultationDurationMinutes;
    private Map<String, Object> availableDays;
    private Boolean enabled;
}