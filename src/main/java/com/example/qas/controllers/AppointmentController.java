package com.example.qas.controllers;

import com.example.qas.dto.request.AppointmentRequest;
import com.example.qas.dto.request.AppointmentRescheduleRequest;
import com.example.qas.dto.response.AppointmentResponse;
import com.example.qas.dto.response.QueueAnalyticsResponse;
import com.example.qas.dto.response.QueueStatusResponse;
import com.example.qas.dto.response.SlotRecommendation;
import com.example.qas.services.AppointmentService;
import com.example.qas.services.QueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final QueueService queueService;

    // === PATIENT ENDPOINTS ===

    @PostMapping("/appointments")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AppointmentResponse> requestAppointment(@Valid @RequestBody AppointmentRequest request) {
        AppointmentResponse response = appointmentService.requestAppointment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/appointments/recommend")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<SlotRecommendation>> getRecommendedSlots(
            @RequestParam Long departmentId,
            @RequestParam LocalDate preferredDate,
            @RequestParam(required = false) LocalTime preferredTime) {
        List<SlotRecommendation> recommendations = appointmentService.getRecommendedSlots(
                departmentId, preferredDate, preferredTime);
        return ResponseEntity.ok(recommendations);
    }

    @GetMapping("/appointments")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<Page<AppointmentResponse>> getMyAppointments(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 10) Pageable pageable) {
        Page<AppointmentResponse> appointments = appointmentService.getMyAppointments(status, pageable);
        return ResponseEntity.ok(appointments);
    }

    @GetMapping("/appointments/{appointmentId}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AppointmentResponse> getAppointmentById(@PathVariable Long appointmentId) {
        AppointmentResponse response = appointmentService.getAppointmentById(appointmentId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/appointments/{appointmentId}/reschedule")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AppointmentResponse> rescheduleAppointment(
            @PathVariable Long appointmentId,
            @Valid @RequestBody AppointmentRescheduleRequest request) {
        AppointmentResponse response = appointmentService.rescheduleAppointment(appointmentId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/appointments/{appointmentId}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<Void> cancelAppointment(@PathVariable Long appointmentId) {
        appointmentService.cancelAppointment(appointmentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/appointments/{appointmentId}/queue")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<QueueStatusResponse> getQueueStatus(@PathVariable Long appointmentId) {
        QueueStatusResponse response = appointmentService.getQueueStatus(appointmentId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/appointments/{appointmentId}/standby")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<String> joinStandbyQueue(@PathVariable Long appointmentId) {
        String response = appointmentService.joinStandbyQueue(appointmentId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/appointments/{appointmentId}/confirm")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AppointmentResponse> confirmAppointment(@PathVariable Long appointmentId) {
        return ResponseEntity.ok(appointmentService.confirmAppointment(appointmentId));
    }

    // === ADMIN ENDPOINTS ===

    @GetMapping("/admin/appointments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AppointmentResponse>> getAllAppointments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AppointmentResponse> appointments = appointmentService.getAllAppointments(
                status, departmentId, doctorId, dateFrom, dateTo, pageable);
        return ResponseEntity.ok(appointments);
    }

    @PatchMapping("/admin/appointments/{appointmentId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppointmentResponse> approveAppointment(
            @PathVariable Long appointmentId,
            @RequestParam Long doctorId) {
        AppointmentResponse response = appointmentService.approveAppointment(appointmentId, doctorId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/admin/appointments/{appointmentId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppointmentResponse> rejectAppointment(@PathVariable Long appointmentId) {
        AppointmentResponse response = appointmentService.rejectAppointment(appointmentId);
        return ResponseEntity.ok(response);
    }



    // === DOCTOR ENDPOINTS ===

    @GetMapping("/doctors/appointments")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<AppointmentResponse>> getDoctorAppointments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate date,
            @RequestParam Long doctorId) {
        List<AppointmentResponse> appointments = appointmentService.getDoctorAppointments(
                doctorId, status, date);
        return ResponseEntity.ok(appointments);
    }
}
