package com.example.qas.services;

import com.example.qas.dto.ai.NoShowPredictionResponse;
import com.example.qas.dto.ai.SlotRecommendationResponse;
import com.example.qas.dto.ai.WaitTimePredictionResponse;
import com.example.qas.dto.request.AppointmentRequest;
import com.example.qas.dto.request.AppointmentRescheduleRequest;
import com.example.qas.dto.response.AppointmentResponse;
import com.example.qas.dto.response.QueueStatusResponse;
import com.example.qas.dto.response.SlotRecommendation;
import com.example.qas.mappers.AppointmentMapper;
import com.example.qas.models.*;
import com.example.qas.models.enums.AppointmentStatus;
import com.example.qas.models.enums.PredictionType;
import com.example.qas.repositories.*;
import com.example.qas.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final AiPredictionLogRepository aiPredictionLogRepository;
    private final AppointmentMapper appointmentMapper;
    private final QueueService queueService;
    private final AIService aiService;
    private final NotificationService notificationService;

    // === Patient operations ===

    @Transactional
    public AppointmentResponse requestAppointment(AppointmentRequest request) {
        Patient patient = getCurrentPatient();

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));

        // Validate date is not in the past
        if (request.getRequestedDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot book appointment in the past");
        }

        // Validate time is within working hours (simplified check)
        if (request.getRequestedTime().isBefore(LocalTime.of(8, 0)) ||
                request.getRequestedTime().isAfter(LocalTime.of(17, 0))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment time must be between 08:00 and 17:00");
        }

        // Create appointment
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDepartment(department);
        appointment.setRequestedDate(request.getRequestedDate());
        appointment.setRequestedTime(request.getRequestedTime());
        appointment.setStatus(AppointmentStatus.PENDING);
        appointment.setReason(request.getReason());
        appointment.setEmergencyFlag(request.getEmergencyFlag() != null && request.getEmergencyFlag());

        appointment = appointmentRepository.save(appointment);

        // If emergency, handle queue priority
        if (appointment.getEmergencyFlag()) {
            queueService.handleEmergencyAppointment(appointment.getId());
        }

        // Run AI no-show prediction
        try {
            NoShowPredictionResponse prediction = aiService.predictNoShow(appointment);
            if (prediction != null) {
                // Log the prediction for future analysis
                logPrediction(appointment, PredictionType.NO_SHOW,
                        Map.of("probability", prediction.getProbability()),
                        Map.of("riskLevel", prediction.getRiskLevel(), "recommendation", prediction.getRecommendation()));

                // If high risk, trigger additional confirmation
                if ("HIGH".equals(prediction.getRiskLevel())) {
                    notificationService.sendConfirmationRequest(appointment.getId());
                }
            }
        } catch (Exception e) {
            log.error("AI no-show prediction failed for appointment {}: {}", appointment.getId(), e.getMessage());
        }

        return appointmentMapper.toResponse(appointment);
    }

    public List<SlotRecommendation> getRecommendedSlots(Long departmentId, LocalDate preferredDate, LocalTime preferredTime) {
        // Use AI service for recommendations
        List<SlotRecommendationResponse.RecommendedSlot> aiSlots =
                aiService.recommendSlots(departmentId, preferredDate, preferredTime);

        if (aiSlots != null && !aiSlots.isEmpty()) {
            return aiSlots.stream()
                    .map(slot -> SlotRecommendation.builder()
                            .date(slot.getDate())
                            .time(slot.getTime())
                            .estimatedWaitMinutes(slot.getEstimatedWaitMinutes())
                            .confidence(slot.getConfidence())
                            .reason(slot.getReason())
                            .build())
                    .collect(Collectors.toList());
        }

        // Fallback to deterministic slot generation
        return generateFallbackSlots(departmentId, preferredDate);
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getAppointmentById(Long appointmentId) {
        Patient currentPatient = getCurrentPatient();
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        // Verify patient owns this appointment
        if (!appointment.getPatient().getId().equals(currentPatient.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this appointment");
        }

        return toPatientResponse(appointment);
    }

    @Transactional(readOnly = true)
    public Page<AppointmentResponse> getMyAppointments(String status, Pageable pageable) {
        Patient patient = getCurrentPatient();

        if (status != null && !status.isBlank()) {
            try {
                AppointmentStatus appointmentStatus = AppointmentStatus.valueOf(status.toUpperCase());
                return appointmentRepository.findByPatientIdAndStatus(patient.getId(), appointmentStatus, pageable)
                        .map(this::toPatientResponse);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status value");
            }
        }

        return appointmentRepository.findByPatientId(patient.getId(), pageable)
                .map(this::toPatientResponse);
    }

    @Transactional
    public AppointmentResponse rescheduleAppointment(Long appointmentId, AppointmentRescheduleRequest request) {
        Patient patient = getCurrentPatient();
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        // Verify patient owns this appointment
        if (!appointment.getPatient().getId().equals(patient.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this appointment");
        }

        // Can only reschedule PENDING or APPROVED appointments
        if (appointment.getStatus() != AppointmentStatus.PENDING &&
                appointment.getStatus() != AppointmentStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot reschedule appointment with status: " + appointment.getStatus());
        }

        // Validate new date is in the future
        if (request.getNewDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot reschedule to a past date");
        }
        if (request.getNewTime().isBefore(LocalTime.of(8, 0)) ||
                request.getNewTime().isAfter(LocalTime.of(17, 0))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Appointment time must be between 08:00 and 17:00");
        }

        // Check if the new slot is available (simplified check)
        if (appointment.getDoctor() != null) {
            List<Appointment> conflicts = appointmentRepository.findConflictingAppointments(
                    appointment.getDoctor().getId(),
                    request.getNewDate(),
                    request.getNewTime(),
                    request.getNewTime().plusMinutes(30)
            );
            if (!conflicts.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The requested slot is already booked");
            }
        }

        // Store old date/time for notification
        LocalDate oldDate = appointment.getRequestedDate();
        LocalTime oldTime = appointment.getRequestedTime();

        // Update appointment
        appointment.setRequestedDate(request.getNewDate());
        appointment.setRequestedTime(request.getNewTime());
        appointment.setStatus(AppointmentStatus.PENDING);
        appointment.setDoctor(null);
        appointment.setConfirmedAt(null);
        appointment.setCancelledAt(null);
        appointment.setCompletedAt(null);
        appointment.setQueuePosition(null);

        appointment = appointmentRepository.save(appointment);

        // Recalculate queue positions for this department
        queueService.recalcQueuePositionsForDepartment(appointment.getDepartment().getId());

        // Send reschedule notification
        try {
            notificationService.sendAppointmentRescheduleNotification(appointment.getId(), oldDate, oldTime);
        } catch (Exception e) {
            log.error("Failed to send reschedule notification: {}", e.getMessage());
        }

        return appointmentMapper.toResponse(appointment);
    }

    @Transactional
    public void cancelAppointment(Long appointmentId) {
        Patient patient = getCurrentPatient();
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        // Verify patient owns this appointment
        if (!appointment.getPatient().getId().equals(patient.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this appointment");
        }

        // Can cancel any appointment except COMPLETED or NO_SHOW
        if (appointment.getStatus() == AppointmentStatus.COMPLETED ||
                appointment.getStatus() == AppointmentStatus.NO_SHOW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot cancel a " + appointment.getStatus().name().toLowerCase() + " appointment");
        }

        Long departmentId = appointment.getDepartment().getId();

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancelledAt(OffsetDateTime.now());
        appointment.setQueuePosition(null);
        appointment.setConfirmedAt(null);
        appointmentRepository.save(appointment);

        // Recalculate queue positions for this department
        queueService.recalcQueuePositionsForDepartment(departmentId);

        // Attempt to allocate a standby slot
        queueService.allocateStandbySlot(departmentId);

        // Send cancellation notification
        try {
            notificationService.sendAppointmentCancellationNotification(appointmentId);
        } catch (Exception e) {
            log.error("Failed to send cancellation notification: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public QueueStatusResponse getQueueStatus(Long appointmentId) {
        Patient patient = getCurrentPatient();
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        if (!appointment.getPatient().getId().equals(patient.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this appointment");
        }
        // Get queue status from QueueService
        QueueStatusResponse response = queueService.getQueueStatusForAppointment(appointmentId);

        // Enhance with AI wait-time prediction
        try {
            WaitTimePredictionResponse aiWait = aiService.predictWaitTime(appointmentId);
            if (aiWait != null) {
                response.setEstimatedWaitMinutes(aiWait.getPredictedWaitMinutes());
                // Optionally store confidence in a separate field if needed
            }
        } catch (Exception e) {
            log.error("AI wait-time prediction failed for appointment {}: {}", appointmentId, e.getMessage());
        }

        return response;
    }

    // === Admin operations ===

    @Transactional(readOnly = true)
    public Page<AppointmentResponse> getAllAppointments(String status, Long departmentId, Long doctorId,
                                                        LocalDate dateFrom, LocalDate dateTo, Pageable pageable) {
        AppointmentStatus appointmentStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                appointmentStatus = AppointmentStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status value");
            }
        }

        Specification<Appointment> specification = (root, query, cb) -> cb.conjunction();
        if (appointmentStatus != null) {
            AppointmentStatus statusFilter = appointmentStatus;
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("status"), statusFilter));
        }
        if (departmentId != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("department").get("id"), departmentId));
        }
        if (doctorId != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("doctor").get("id"), doctorId));
        }
        if (dateFrom != null) {
            specification = specification.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("requestedDate"), dateFrom));
        }
        if (dateTo != null) {
            specification = specification.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("requestedDate"), dateTo));
        }

        return appointmentRepository.findAll(specification, pageable)
                .map(this::toPatientResponse);
    }

    @Transactional
    public AppointmentResponse approveAppointment(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only pending appointments can be approved");
        }

        // Assignment is automatic: choose an eligible doctor for the
        // department's hospital/specialty and requested time slot.
        Doctor doctor = queueService.findAvailableDoctor(appointment);
        if (doctor == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No available doctor matches this department and requested time");
        }

        appointment.setDoctor(doctor);
        appointment.setStatus(AppointmentStatus.APPROVED);
        appointment = appointmentRepository.save(appointment);

        // Recalculate queue positions for this department
        queueService.recalcQueuePositionsForDepartment(appointment.getDepartment().getId());

        // Send approval notification
        try {
            notificationService.sendAppointmentApprovalNotification(appointmentId);
        } catch (Exception e) {
            log.error("Failed to send approval notification: {}", e.getMessage());
        }

        // Run AI no-show prediction for approved appointment
        try {
            NoShowPredictionResponse prediction = aiService.predictNoShow(appointment);
            if (prediction != null && "HIGH".equals(prediction.getRiskLevel())) {
                notificationService.sendConfirmationRequest(appointmentId);
            }
        } catch (Exception e) {
            log.error("AI no-show prediction failed for appointment {}: {}", appointmentId, e.getMessage());
        }

        return appointmentMapper.toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse rejectAppointment(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only pending appointments can be rejected");
        }

        appointment.setStatus(AppointmentStatus.REJECTED);
        appointment = appointmentRepository.save(appointment);

        // No queue recalculation needed for rejected appointments

        return appointmentMapper.toResponse(appointment);
    }

    // === Doctor operations ===

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getDoctorAppointments(Long doctorId, String status, LocalDate date) {
        Doctor doctor = getCurrentDoctor();

        // Ensure doctor is viewing their own appointments
        if (!doctor.getId().equals(doctorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only view your own appointments");
        }

        AppointmentStatus appointmentStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                appointmentStatus = AppointmentStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status value");
            }
        }

        List<Appointment> appointments;
        if (appointmentStatus != null && date != null) {
            appointments = appointmentRepository.findByDoctorIdAndStatusAndRequestedDate(
                    doctorId, appointmentStatus, date);
        } else if (appointmentStatus != null) {
            appointments = appointmentRepository.findByDoctorIdAndStatus(doctorId, appointmentStatus);
        } else if (date != null) {
            appointments = appointmentRepository.findByDoctorIdAndRequestedDate(doctorId, date);
        } else {
            appointments = appointmentRepository.findByDoctorId(doctorId);
        }

        return appointments.stream()
                .map(appointmentMapper::toResponse)
                .collect(Collectors.toList());
    }

    // === Standby management ===

    @Transactional
    public String joinStandbyQueue(Long appointmentId) {
        Patient patient = getCurrentPatient();
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        if (!appointment.getPatient().getId().equals(patient.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this appointment");
        }

        // Only PENDING appointments can join standby
        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only pending appointments can join standby");
        }

        if (Boolean.TRUE.equals(appointment.getStandbyRequested())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Appointment is already in the standby queue");
        }
        appointment.setStandbyRequested(true);
        appointmentRepository.save(appointment);
        return "Successfully joined standby queue for appointment " + appointmentId;
    }

    @Transactional
    public AppointmentResponse confirmAppointment(Long appointmentId) {
        Patient patient = getCurrentPatient();
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        if (!appointment.getPatient().getId().equals(patient.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this appointment");
        }
        if (appointment.getStatus() != AppointmentStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only approved appointments can be confirmed");
        }
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setConfirmedAt(OffsetDateTime.now());
        appointmentRepository.save(appointment);
        queueService.recalcQueuePositionsForDepartment(appointment.getDepartment().getId());
        return appointmentMapper.toResponse(appointment);
    }

    // === Helper methods ===

    private Patient getCurrentPatient() {
        User currentUser = SecurityUtils.getCurrentUser(userRepository);
        return patientRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient profile not found"));
    }

    private Doctor getCurrentDoctor() {
        User currentUser = SecurityUtils.getCurrentUser(userRepository);
        return doctorRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor profile not found"));
    }

    private AppointmentResponse toPatientResponse(Appointment appointment) {
        AppointmentResponse response = appointmentMapper.toResponse(appointment);
        if (response.getEstimatedWaitTimeMinutes() == null && appointment.getQueuePosition() != null) {
            int duration = appointment.getDepartment().getEstimatedConsultationDurationMinutes();
            response.setEstimatedWaitTimeMinutes(Math.max(0, appointment.getQueuePosition() - 1) * duration);
        }
        return response;
    }

    private List<SlotRecommendation> generateFallbackSlots(Long departmentId, LocalDate date) {
        List<SlotRecommendation> recommendations = new ArrayList<>();
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));

        int slotDuration = department.getEstimatedConsultationDurationMinutes();
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(17, 0);

        while (start.isBefore(end)) {
            recommendations.add(SlotRecommendation.builder()
                    .date(date)
                    .time(start)
                    .estimatedWaitMinutes(15 + (int) (Math.random() * 30))
                    .confidence(0.7 + Math.random() * 0.3)
                    .reason("Available slot based on department schedule")
                    .build());
            start = start.plusMinutes(slotDuration);
        }

        return recommendations;
    }

    private void logPrediction(Appointment appointment, PredictionType type,
                               Map<String, Object> features, Map<String, Object> result) {
        try {
            AiPredictionLog log = new AiPredictionLog();
            log.setAppointment(appointment);
            log.setPredictionType(type);
            log.setInputFeatures(features);
            log.setPredictionResult(result);
            log.setModelVersion("v1.0");
            log.setCreatedAt(OffsetDateTime.now());
            aiPredictionLogRepository.save(log);
        } catch (Exception e) {
            log.error("Failed to log AI prediction: {}", e.getMessage());
        }
    }
}
