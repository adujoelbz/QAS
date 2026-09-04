package com.example.qas.services;

import com.example.qas.dto.ai.*;
import com.example.qas.models.Appointment;
import com.example.qas.models.Department;
import com.example.qas.repositories.AppointmentRepository;
import com.example.qas.models.enums.AppointmentStatus;
import com.example.qas.repositories.DepartmentRepository;
import com.example.qas.repositories.NotificationRepository;
import com.example.qas.models.enums.NotificationStatus;
import com.example.qas.services.ai.AIServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    private final AIServiceClient aiClient;
    private final DepartmentRepository departmentRepository;
    private final AppointmentRepository appointmentRepository;
    private final NotificationRepository notificationRepository;

    @Value("${ai.service.enabled:true}")
    private boolean aiEnabled;

    // === Slot Recommendations with Fallback ===

    public List<SlotRecommendationResponse.RecommendedSlot> recommendSlots(
            Long departmentId, LocalDate preferredDate, LocalTime preferredTime) {

        // Try AI first
        if (aiEnabled) {
            SlotRecommendationRequest request = SlotRecommendationRequest.builder()
                    .departmentId(departmentId)
                    .preferredDate(preferredDate)
                    .preferredTime(preferredTime)
                    .slotDurationMinutes(getSlotDuration(departmentId))
                    .queueLength(getQueueLength(departmentId))
                    .build();

            SlotRecommendationResponse response = aiClient.recommendSlots(request);
            if (response != null && response.getSlots() != null && !response.getSlots().isEmpty()) {
                return response.getSlots();
            }
            log.warn("AI slot recommendation returned empty or null – using fallback.");
        }

        // Fallback: generate deterministic slots
        return generateFallbackSlots(departmentId, preferredDate);
    }

    // === No-Show Prediction with Fallback ===

    public NoShowPredictionResponse predictNoShow(Appointment appointment) {
        if (aiEnabled) {
            NoShowPredictionRequest request = NoShowPredictionRequest.builder()
                    .patientId(appointment.getPatient().getId())
                    .doctorId(appointment.getDoctor() != null ? appointment.getDoctor().getId() : null)
                    .appointmentDate(appointment.getRequestedDate())
                    .appointmentTime(appointment.getRequestedTime())
                    .previousNoShows((int) appointmentRepository.countByPatientIdAndStatus(
                            appointment.getPatient().getId(), AppointmentStatus.NO_SHOW))
                    .reminderSent(notificationRepository.existsByAppointmentIdAndSubjectAndStatus(
                            appointment.getId(), "Appointment Reminder", NotificationStatus.SENT))
                    .queuePosition(appointment.getQueuePosition())
                    .emergencyFlag(Boolean.TRUE.equals(appointment.getEmergencyFlag()))
                    .dayOfWeek(appointment.getRequestedDate().getDayOfWeek().name())
                    .build();

            NoShowPredictionResponse response = aiClient.predictNoShow(request);
            if (response != null) {
                return response;
            }
            log.warn("AI no-show prediction failed – using fallback.");
        }

        // Fallback: simple heuristic
        double probability = 0.05; // default 5%
        if (appointment.getRequestedTime().isBefore(LocalTime.of(9, 0))) {
            probability = 0.02; // early morning more likely to show
        } else if (appointment.getRequestedTime().isAfter(LocalTime.of(16, 0))) {
            probability = 0.12; // late afternoon higher no-show
        }
        return NoShowPredictionResponse.builder()
                .probability(probability)
                .riskLevel(probability > 0.1 ? "HIGH" : probability > 0.05 ? "MEDIUM" : "LOW")
                .recommendation(probability > 0.1 ? "Send reminder and ask for confirmation" : "Normal")
                .build();
    }

    // === Wait-Time Prediction with Fallback ===

    public WaitTimePredictionResponse predictWaitTime(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (aiEnabled) {
            // Get queue info
            List<Appointment> activeQueue = appointmentRepository
                    .findActiveQueueByDepartment(appointment.getDepartment().getId());
            int position = 0;
            for (int i = 0; i < activeQueue.size(); i++) {
                if (activeQueue.get(i).getId().equals(appointmentId)) {
                    position = i + 1;
                    break;
                }
            }

            WaitTimePredictionRequest request = WaitTimePredictionRequest.builder()
                    .departmentId(appointment.getDepartment().getId())
                    .date(appointment.getRequestedDate())
                    .time(appointment.getRequestedTime())
                    .queuePosition(position)
                    .patientsAhead(Math.max(0, position - 1))
                    .averageConsultationDuration(
                            appointment.getDepartment().getEstimatedConsultationDurationMinutes())
                    .currentQueueLength(activeQueue.size())
                    .build();

            WaitTimePredictionResponse response = aiClient.predictWaitTime(request);
            if (response != null) {
                return response;
            }
            log.warn("AI wait-time prediction failed – using fallback.");
        }

        // Fallback: deterministic
        int avgDuration = appointment.getDepartment().getEstimatedConsultationDurationMinutes();
        List<Appointment> activeQueue = appointmentRepository
                .findActiveQueueByDepartment(appointment.getDepartment().getId());
        int position = 0;
        for (int i = 0; i < activeQueue.size(); i++) {
            if (activeQueue.get(i).getId().equals(appointmentId)) {
                position = i + 1;
                break;
            }
        }
        int wait = Math.max(0, position - 1) * avgDuration;
        return WaitTimePredictionResponse.builder()
                .predictedWaitMinutes(wait)
                .confidence(75)
                .build();
    }

    // === Helper methods ===

    private int getSlotDuration(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .map(Department::getEstimatedConsultationDurationMinutes)
                .orElse(30);
    }

    private int getQueueLength(Long departmentId) {
        return appointmentRepository.findActiveQueueByDepartment(departmentId).size();
    }

    private List<SlotRecommendationResponse.RecommendedSlot> generateFallbackSlots(
            Long departmentId, LocalDate date) {
        int duration = getSlotDuration(departmentId);
        List<SlotRecommendationResponse.RecommendedSlot> slots = new ArrayList<>();
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(17, 0);
        while (start.isBefore(end)) {
            slots.add(SlotRecommendationResponse.RecommendedSlot.builder()
                    .date(date)
                    .time(start)
                    .estimatedWaitMinutes(15)
                    .confidence(0.7)
                    .reason("Available slot based on department schedule")
                    .build());
            start = start.plusMinutes(duration);
        }
        return slots;
    }
}
