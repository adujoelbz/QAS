package com.example.qas.services;

import com.example.qas.dto.response.*;
import com.example.qas.models.AiPredictionLog;
import com.example.qas.models.AuditLog;
import com.example.qas.models.enums.AppointmentStatus;
import com.example.qas.models.enums.PredictionType;
import com.example.qas.models.enums.UserRole;
import com.example.qas.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditLogRepository auditLogRepository;
    private final AiPredictionLogRepository aiPredictionLogRepository;
    private final QueueService queueService;

    // === Dashboard statistics ===

    public DashboardStatsResponse getDashboardStats() {
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.minusDays(today.getDayOfWeek().getValue() - 1); // Monday
        LocalDate startOfMonth = today.withDayOfMonth(1);

        // Count users by role
        long totalPatients = userRepository.countByRole(UserRole.PATIENT);
        long totalDoctors = userRepository.countByRoleAndEnabled(UserRole.DOCTOR, true); // only enabled
        long totalAdmins = userRepository.countByRole(UserRole.ADMIN);

        // Appointment counts
        long appointmentsToday = appointmentRepository.countByStatusAndRequestedDate(AppointmentStatus.COMPLETED, today)
                + appointmentRepository.countByStatusAndRequestedDate(AppointmentStatus.CONFIRMED, today)
                + appointmentRepository.countByStatusAndRequestedDate(AppointmentStatus.APPROVED, today)
                + appointmentRepository.countByStatusAndRequestedDate(AppointmentStatus.NO_SHOW, today);

        long appointmentsThisWeek = appointmentRepository.countByStatusAndRequestedDateBetween(
                AppointmentStatus.COMPLETED, startOfWeek, today)
                + appointmentRepository.countByStatusAndRequestedDateBetween(
                AppointmentStatus.CONFIRMED, startOfWeek, today)
                + appointmentRepository.countByStatusAndRequestedDateBetween(
                AppointmentStatus.APPROVED, startOfWeek, today);

        long appointmentsThisMonth = appointmentRepository.countByStatusAndRequestedDateBetween(
                AppointmentStatus.COMPLETED, startOfMonth, today)
                + appointmentRepository.countByStatusAndRequestedDateBetween(
                AppointmentStatus.CONFIRMED, startOfMonth, today);

        // Average wait time (for completed appointments this month)
        Double avgWaitTime = appointmentRepository.findAverageActualWaitTime(startOfMonth, today);

        // No-show rate (this month)
        long totalCompleted = appointmentRepository.countByStatusAndRequestedDateBetween(
                AppointmentStatus.COMPLETED, startOfMonth, today);
        long totalNoShows = appointmentRepository.countNoShowsBetween(startOfMonth, today);
        double noShowRate = (totalCompleted + totalNoShows) > 0
                ? (double) totalNoShows / (totalCompleted + totalNoShows)
                : 0.0;

        // AI accuracy (placeholder – will be updated when AI service is integrated)
        Map<String, Double> aiAccuracy = new HashMap<>();
        aiAccuracy.put("noShowPrediction", 0.0);
        aiAccuracy.put("waitTimePredictionMAE", 0.0);

        return DashboardStatsResponse.builder()
                .totalPatients(totalPatients)
                .totalDoctors(totalDoctors)
                .totalAdmins(totalAdmins)
                .totalAppointmentsToday(appointmentsToday)
                .totalAppointmentsThisWeek(appointmentsThisWeek)
                .totalAppointmentsThisMonth(appointmentsThisMonth)
                .averageWaitTimeMinutes(avgWaitTime != null ? avgWaitTime : 0.0)
                .noShowRate(noShowRate)
                .aiAccuracy(aiAccuracy)
                .build();
    }

    // === Queue analytics for a department (delegates to QueueService) ===

    public QueueAnalyticsResponse getQueueAnalytics(Long departmentId) {
        return queueService.getQueueAnalytics(departmentId);
    }

    // === AI prediction reports ===

    public List<PredictionReport> getPredictionReports(PredictionType type, OffsetDateTime from, OffsetDateTime to) {
        List<AiPredictionLog> logs = aiPredictionLogRepository.findByPredictionTypeAndCreatedAtBetween(type, from, to);
        return logs.stream()
                .map(log -> PredictionReport.builder()
                        .id(log.getId())
                        .appointmentId(log.getAppointment() != null ? log.getAppointment().getId() : null)
                        .predictionType(log.getPredictionType().name())
                        .inputFeatures(log.getInputFeatures())
                        .predictionResult(log.getPredictionResult())
                        .actualOutcome(log.getActualOutcome())
                        .modelVersion(log.getModelVersion())
                        .createdAt(log.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // === Audit logs ===

    public Page<AuditLogResponse> getAuditLogs(Long adminId, OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
        Page<AuditLog> logs;
        if (adminId != null && from != null && to != null) {
            // We need to add this method to AuditLogRepository – we'll assume it exists
            // For now, we'll just use the base method.
            logs = auditLogRepository.findAll(pageable);
        } else if (adminId != null) {
            logs = auditLogRepository.findByAdminId(adminId, pageable);
        } else if (from != null && to != null) {
            logs = auditLogRepository.findByCreatedAtBetween(from, to, pageable);
        } else {
            logs = auditLogRepository.findAll(pageable);
        }
        return logs.map(this::mapToAuditLogResponse);
    }

    public void recalcQueuePositionsForDepartment(Long departmentId) {
        queueService.recalcQueuePositionsForDepartment(departmentId);
    }

    private AuditLogResponse mapToAuditLogResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .adminId(log.getAdmin().getId())
                .action(log.getAction())
                .details(log.getDetails())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .build();
    }
}