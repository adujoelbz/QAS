package com.example.qas.controllers;

import com.example.qas.dto.response.AuditLogResponse;
import com.example.qas.dto.response.DashboardStatsResponse;
import com.example.qas.dto.response.PredictionReport;
import com.example.qas.dto.response.QueueAnalyticsResponse;
import com.example.qas.models.enums.PredictionType;
import com.example.qas.services.AdminService;
import com.example.qas.services.DoctorService;
import com.example.qas.services.NotificationService;
import com.example.qas.services.TrainingDataExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.time.LocalDate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final DoctorService doctorService;
    private final NotificationService notificationService;
    private final TrainingDataExportService trainingDataExportService;

    @GetMapping(value = "/ai/training-data", produces = "text/csv")
    public ResponseEntity<String> exportTrainingData(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        if (to.isBefore(from)) return ResponseEntity.badRequest().body("from must be on or before to\n");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=training-data-" + from + "-" + to + ".csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(trainingDataExportService.exportCsv(from, to));
    }

    @PostMapping("/notifications/{notificationId}/retry")
    public ResponseEntity<Void> retryNotification(@PathVariable Long notificationId) {
        notificationService.retryFailedNotification(notificationId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/doctors/{userId}/approve")
    public ResponseEntity<Void> approveDoctor(@PathVariable Long userId,
                                              @RequestParam Long hospitalId,
                                              @RequestParam boolean approved) {
        doctorService.approveDoctorRegistration(userId, hospitalId, approved);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<DashboardStatsResponse> getDashboardStats() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    @GetMapping("/queue/departments/{departmentId}")
    public ResponseEntity<QueueAnalyticsResponse> getQueueAnalytics(@PathVariable Long departmentId) {
        return ResponseEntity.ok(adminService.getQueueAnalytics(departmentId));
    }

    @PostMapping("/queue/departments/{departmentId}/recalc")
    public ResponseEntity<Void> recalcQueue(@PathVariable Long departmentId) {
        adminService.recalcQueuePositionsForDepartment(departmentId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/ai/predictions")
    public ResponseEntity<List<PredictionReport>> getPredictionReports(
            @RequestParam(required = false) PredictionType type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        List<PredictionReport> reports = adminService.getPredictionReports(type, from, to);
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AuditLogResponse> logs = adminService.getAuditLogs(adminId, from, to, pageable);
        return ResponseEntity.ok(logs);
    }
}
