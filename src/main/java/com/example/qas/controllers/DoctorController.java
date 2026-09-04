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
import java.util.Map;

@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;

    // === Profile and availability ===

    @GetMapping("/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<DoctorProfileResponse> getMyProfile() {
        return ResponseEntity.ok(doctorService.getCurrentDoctorProfile());
    }

    @PutMapping("/me/availability")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<DoctorProfileResponse> updateAvailability(
            @Valid @RequestBody DoctorAvailabilityRequest request) {
        return ResponseEntity.ok(doctorService.updateDoctorAvailability(request));
    }

    @PutMapping("/me/consultation-duration")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<DoctorProfileResponse> setConsultationDuration(
            @Valid @RequestBody ConsultationDurationRequest request) {
        return ResponseEntity.ok(doctorService.setConsultationDuration(request));
    }

    // === Schedule and appointments ===

    @GetMapping("/me/schedule")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<DoctorScheduleResponse>> getSchedule(
            @RequestParam LocalDate dateFrom,
            @RequestParam LocalDate dateTo) {
        return ResponseEntity.ok(doctorService.getDoctorSchedule(dateFrom, dateTo));
    }

    @GetMapping("/me/appointments")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<AppointmentResponse>> getAppointments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate date) {
        return ResponseEntity.ok(doctorService.getDoctorAppointments(status, date));
    }

    @GetMapping("/appointments/{appointmentId}/medical-history/download")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Map<String, String>> downloadPatientMedicalHistory(
            @PathVariable Long appointmentId,
            @RequestParam String publicId) {
        DoctorService.MedicalHistoryFileResource file =
                doctorService.getMedicalHistoryFileResource(appointmentId, publicId);
        return ResponseEntity.ok(Map.of(
                "downloadUrl", file.url(),
                "originalFilename", file.originalFilename()));
    }

    @PatchMapping("/appointments/{appointmentId}/status")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<AppointmentResponse> updateAppointmentStatus(
            @PathVariable Long appointmentId,
            @RequestParam String status,
            @RequestParam(required = false) Integer actualWaitTime) {
        return ResponseEntity.ok(doctorService.updateConsultationStatus(appointmentId, status, actualWaitTime));
    }

    @PostMapping("/appointments/{appointmentId}/start")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<AppointmentResponse> startConsultation(@PathVariable Long appointmentId) {
        return ResponseEntity.ok(doctorService.startConsultation(appointmentId));
    }

}
