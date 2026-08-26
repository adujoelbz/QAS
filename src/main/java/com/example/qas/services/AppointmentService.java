package com.example.qas.services;

import com.example.qas.dto.request.AppointmentRequest;
import com.example.qas.dto.request.AppointmentRescheduleRequest;
import com.example.qas.dto.response.AppointmentResponse;
import com.example.qas.dto.response.QueueStatusResponse;
import com.example.qas.dto.response.SlotRecommendation;
import com.example.qas.mappers.AppointmentMapper;
import com.example.qas.models.*;
import com.example.qas.models.enums.AppointmentStatus;
import com.example.qas.repositories.*;
import com.example.qas.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final AppointmentMapper appointmentMapper;
    private final QueueService queueService;

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

        // Trigger AI recommendation (async if possible) - we'll implement later
        // For now, we just return the appointment

        return appointmentMapper.toResponse(appointment);
    }

    public List<SlotRecommendation> getRecommendedSlots(Long departmentId, LocalDate preferredDate, LocalTime preferredTime) {
        // Basic implementation – we'll enhance with AI later
        List<SlotRecommendation> recommendations = new ArrayList<>();

        // Generate time slots from 08:00 to 17:00
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(17, 0);

        // Get department's average consultation duration
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));
        int slotDuration = department.getEstimatedConsultationDurationMinutes();

        // Create slots
        LocalTime current = start;
        while (current.isBefore(end)) {
            SlotRecommendation slot = SlotRecommendation.builder()
                    .date(preferredDate)
                    .time(current)
                    .estimatedWaitMinutes(15 + (int) (Math.random() * 30)) // Placeholder
                    .confidence(0.7 + Math.random() * 0.3) // Placeholder
                    .reason("Available slot based on department schedule")
                    .build();
            recommendations.add(slot);
            current = current.plusMinutes(slotDuration);
        }

        return recommendations;
    }

    public AppointmentResponse getAppointmentById(Long appointmentId) {
        Patient currentPatient = getCurrentPatient();
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        // Verify patient owns this appointment
        if (!appointment.getPatient().getId().equals(currentPatient.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this appointment");
        }

        return appointmentMapper.toResponse(appointment);
    }

    public Page<AppointmentResponse> getMyAppointments(String status, Pageable pageable) {
        Patient patient = getCurrentPatient();

        if (status != null && !status.isBlank()) {
            try {
                AppointmentStatus appointmentStatus = AppointmentStatus.valueOf(status.toUpperCase());
                return appointmentRepository.findByPatientIdAndStatus(patient.getId(), appointmentStatus, pageable)
                        .map(appointmentMapper::toResponse);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status value");
            }
        }

        return appointmentRepository.findByPatientId(patient.getId(), pageable)
                .map(appointmentMapper::toResponse);
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

        // Update appointment
        appointment.setRequestedDate(request.getNewDate());
        appointment.setRequestedTime(request.getNewTime());
        appointment.setStatus(AppointmentStatus.PENDING);

        appointment = appointmentRepository.save(appointment);

        // Recalculate queue positions for this department
        queueService.recalcQueuePositionsForDepartment(appointment.getDepartment().getId());

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
        appointment.setCancelledAt(java.time.OffsetDateTime.now());
        appointmentRepository.save(appointment);

        // Recalculate queue positions for this department
        queueService.recalcQueuePositionsForDepartment(departmentId);

        // Attempt to allocate a standby slot
        queueService.allocateStandbySlot(departmentId);
    }

    public QueueStatusResponse getQueueStatus(Long appointmentId) {
        // Delegate to QueueService for consistent queue management
        return queueService.getQueueStatusForAppointment(appointmentId);
    }

    // === Admin operations ===

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

        return appointmentRepository.findAppointmentsWithFilters(
                        appointmentStatus, departmentId, doctorId, dateFrom, dateTo, pageable)
                .map(appointmentMapper::toResponse);
    }

    @Transactional
    public AppointmentResponse approveAppointment(Long appointmentId, Long doctorId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only pending appointments can be approved");
        }

        // Verify doctor exists and belongs to the same department/hospital
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        // Check if doctor belongs to the same hospital as the department
        if (!doctor.getHospital().getId().equals(appointment.getDepartment().getHospital().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Doctor must belong to the same hospital as the department");
        }

        // Check if doctor is available at that time
        List<Appointment> conflicts = appointmentRepository.findConflictingAppointments(
                doctorId,
                appointment.getRequestedDate(),
                appointment.getRequestedTime(),
                appointment.getRequestedTime().plusMinutes(30)
        );
        if (!conflicts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Doctor is already booked at this time");
        }

        appointment.setDoctor(doctor);
        appointment.setStatus(AppointmentStatus.APPROVED);
        appointment = appointmentRepository.save(appointment);

        // Recalculate queue positions for this department
        queueService.recalcQueuePositionsForDepartment(appointment.getDepartment().getId());

        // Send notification (will be implemented in NotificationService)
        // notificationService.sendAppointmentApprovalNotification(appointment);

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

        // In a real implementation, there would be a separate StandbyQueue entity.
        // For now, we just return a message.
        return "Successfully joined standby queue for appointment " + appointmentId;
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
}