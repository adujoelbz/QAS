package com.example.qas.controllers;

import com.example.qas.dto.request.ConsultationDurationRequest;
import com.example.qas.dto.request.DoctorAvailabilityRequest;
import com.example.qas.dto.response.AppointmentResponse;
import com.example.qas.dto.response.DoctorProfileResponse;
import com.example.qas.dto.response.DoctorScheduleResponse;
import com.example.qas.services.DoctorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorController {

    private final DoctorService doctorService;

    // === Profile and availability ===

    @GetMapping("/me")
    public ResponseEntity<DoctorProfileResponse> getMyProfile() {
        return ResponseEntity.ok(doctorService.getCurrentDoctorProfile());
    }

    @PutMapping("/me/availability")
    public ResponseEntity<DoctorProfileResponse> updateAvailability(
            @Valid @RequestBody DoctorAvailabilityRequest request) {
        return ResponseEntity.ok(doctorService.updateDoctorAvailability(request));
    }

    @PutMapping("/me/consultation-duration")
    public ResponseEntity<DoctorProfileResponse> setConsultationDuration(
            @Valid @RequestBody ConsultationDurationRequest request) {
        return ResponseEntity.ok(doctorService.setConsultationDuration(request));
    }

    // === Schedule and appointments ===

    @GetMapping("/me/schedule")
    public ResponseEntity<List<DoctorScheduleResponse>> getSchedule(
            @RequestParam LocalDate dateFrom,
            @RequestParam LocalDate dateTo) {
        return ResponseEntity.ok(doctorService.getDoctorSchedule(dateFrom, dateTo));
    }

    @GetMapping("/me/appointments")
    public ResponseEntity<List<AppointmentResponse>> getAppointments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate date) {
        return ResponseEntity.ok(doctorService.getDoctorAppointments(status, date));
    }

    @PatchMapping("/appointments/{appointmentId}/status")
    public ResponseEntity<AppointmentResponse> updateAppointmentStatus(
            @PathVariable Long appointmentId,
            @RequestParam String status,
            @RequestParam(required = false) Integer actualWaitTime) {
        return ResponseEntity.ok(doctorService.updateConsultationStatus(appointmentId, status, actualWaitTime));
    }

    @PatchMapping("/admin/doctors/{userId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> approveDoctor(@PathVariable Long userId,
                                              @RequestParam Long hospitalId,
                                              @RequestParam boolean approved) {
        doctorService.approveDoctorRegistration(userId, hospitalId, approved);
        return ResponseEntity.noContent().build();
    }
}