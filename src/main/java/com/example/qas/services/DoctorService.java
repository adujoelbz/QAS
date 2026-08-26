package com.example.qas.services;

import com.example.qas.dto.request.ConsultationDurationRequest;
import com.example.qas.dto.request.DoctorAvailabilityRequest;
import com.example.qas.dto.response.AppointmentResponse;
import com.example.qas.dto.response.DoctorProfileResponse;
import com.example.qas.dto.response.DoctorScheduleResponse;
import com.example.qas.mappers.AppointmentMapper;
import com.example.qas.mappers.DoctorMapper;
import com.example.qas.models.Appointment;
import com.example.qas.models.Doctor;
import com.example.qas.models.Hospital;
import com.example.qas.models.User;
import com.example.qas.models.enums.AppointmentStatus;
import com.example.qas.repositories.AppointmentRepository;
import com.example.qas.repositories.DoctorRepository;
import com.example.qas.repositories.HospitalRepository;
import com.example.qas.repositories.UserRepository;
import com.example.qas.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final HospitalRepository hospitalRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentMapper appointmentMapper;
    private final DoctorMapper doctorMapper;
    private final QueueService queueService;            // Added
    private final NotificationService notificationService; // Added
    private final AppointmentTransitionPolicy appointmentTransitionPolicy;

    // === Doctor's own profile ===

    public DoctorProfileResponse getCurrentDoctorProfile() {
        Doctor doctor = getCurrentDoctor();
        return mapToDoctorProfileResponse(doctor);
    }

    @Transactional
    public DoctorProfileResponse updateDoctorAvailability(DoctorAvailabilityRequest request) {
        Doctor doctor = getCurrentDoctor();
        doctor.setAvailableDays(request.getAvailableDays());
        doctor = doctorRepository.save(doctor);
        return mapToDoctorProfileResponse(doctor);
    }

    @Transactional
    public DoctorProfileResponse setConsultationDuration(ConsultationDurationRequest request) {
        Doctor doctor = getCurrentDoctor();
        doctor.setConsultationDurationMinutes(request.getConsultationDurationMinutes());
        doctor = doctorRepository.save(doctor);
        return mapToDoctorProfileResponse(doctor);
    }

    // === Schedule and appointments ===

    public List<DoctorScheduleResponse> getDoctorSchedule(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null || dateTo == null || dateTo.isBefore(dateFrom)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "dateTo must be on or after dateFrom");
        }
        Doctor doctor = getCurrentDoctor();
        List<Appointment> appointments = appointmentRepository
                .findByDoctorIdAndRequestedDateBetween(doctor.getId(), dateFrom, dateTo);
        Map<LocalDate, List<Appointment>> appointmentsByDate = appointments.stream()
                .collect(Collectors.groupingBy(Appointment::getRequestedDate));

        List<DoctorScheduleResponse> schedule = new ArrayList<>();
        LocalDate current = dateFrom;
        while (!current.isAfter(dateTo)) {
            List<AppointmentResponse> appResponses = appointmentsByDate.getOrDefault(current, List.of()).stream()
                    .map(appointmentMapper::toResponse)
                    .collect(Collectors.toList());
            schedule.add(DoctorScheduleResponse.builder()
                    .date(current)
                    .appointments(appResponses)
                    .availableSlots(new ArrayList<>())
                    .build());
            current = current.plusDays(1);
        }
        return schedule;
    }

    public List<AppointmentResponse> getDoctorAppointments(String status, LocalDate date) {
        Doctor doctor = getCurrentDoctor();
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
            appointments = appointmentRepository
                    .findByDoctorIdAndStatusAndRequestedDate(doctor.getId(), appointmentStatus, date);
        } else if (appointmentStatus != null) {
            appointments = appointmentRepository
                    .findByDoctorIdAndStatus(doctor.getId(), appointmentStatus);
        } else if (date != null) {
            appointments = appointmentRepository
                    .findByDoctorIdAndRequestedDate(doctor.getId(), date);
        } else {
            appointments = appointmentRepository
                    .findByDoctorId(doctor.getId());
        }
        return appointments.stream()
                .map(appointmentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AppointmentResponse startConsultation(Long appointmentId) {
        Doctor doctor = getCurrentDoctor();
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        if (appointment.getDoctor() == null || !appointment.getDoctor().getId().equals(doctor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not assigned to this appointment");
        }
        if (appointment.getStatus() != AppointmentStatus.APPROVED && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only approved or confirmed appointments can start");
        }
        if (appointment.getConsultationStartedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Consultation has already started");
        }
        appointment.setConsultationStartedAt(OffsetDateTime.now());
        return appointmentMapper.toResponse(appointmentRepository.save(appointment));
    }

    @Transactional
    public AppointmentResponse updateConsultationStatus(Long appointmentId, String status, Integer actualWaitTime) {
        Doctor doctor = getCurrentDoctor();
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        if (appointment.getDoctor() == null || !appointment.getDoctor().getId().equals(doctor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not assigned to this appointment");
        }

        AppointmentStatus newStatus;
        try {
            newStatus = AppointmentStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status value");
        }

        appointmentTransitionPolicy.validateDoctorOutcome(appointment.getStatus(), newStatus);
        if (actualWaitTime != null && actualWaitTime < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Actual wait time cannot be negative");
        }
        if (actualWaitTime != null) {
            appointment.setActualWaitTimeMinutes(actualWaitTime);
        }
        if (newStatus == AppointmentStatus.COMPLETED) {
            if (appointment.getConsultationStartedAt() == null) {
                appointment.setConsultationStartedAt(OffsetDateTime.now());
            }
            appointment.setConsultationEndedAt(OffsetDateTime.now());
            appointment.setCompletedAt(OffsetDateTime.now());
            appointment.setCancelledAt(null);
        } else {
            appointment.setCompletedAt(null);
        }

        appointment.setStatus(newStatus);
        appointment = appointmentRepository.save(appointment);

        // Recalculate queue positions for this department
        queueService.recalcQueuePositionsForDepartment(appointment.getDepartment().getId());

        // Send notification based on status change
        if (newStatus == AppointmentStatus.NO_SHOW) {
            notificationService.sendNoShowAlert(appointmentId);
            // Attempt to allocate a standby slot
            queueService.allocateStandbySlot(appointment.getDepartment().getId());
        } else if (newStatus == AppointmentStatus.COMPLETED) {
            log.info("Appointment {} marked as COMPLETED", appointmentId);
        }

        return appointmentMapper.toResponse(appointment);
    }

    // === Admin operations (approve doctor registration) ===

    @Transactional
    public DoctorProfileResponse approveDoctorRegistration(Long doctorUserId, Long hospitalId, boolean approved) {
        if (!approved) {
            User user = userRepository.findById(doctorUserId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            user.setEnabled(false);
            userRepository.save(user);
            return null;
        }

        User user = userRepository.findById(doctorUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!user.getRole().name().equals("DOCTOR")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not a doctor");
        }

        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hospital not found"));

        Doctor doctor = doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor profile not found"));

        doctor.setHospital(hospital);
        doctorRepository.save(doctor);

        user.setEnabled(true);
        userRepository.save(user);

        return mapToDoctorProfileResponse(doctor);
    }

    // === Helper methods ===

    private Doctor getCurrentDoctor() {
        User currentUser = SecurityUtils.getCurrentUser(userRepository);
        return doctorRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor profile not found"));
    }

    private DoctorProfileResponse mapToDoctorProfileResponse(Doctor doctor) {
        return DoctorProfileResponse.builder()
                .id(doctor.getId())
                .email(doctor.getUser().getEmail())
                .firstName(doctor.getFirstName())
                .lastName(doctor.getLastName())
                .specialty(doctor.getSpecialty())
                .hospitalId(doctor.getHospital() != null ? doctor.getHospital().getId() : null)
                .hospitalName(doctor.getHospital() != null ? doctor.getHospital().getName() : null)
                .consultationDurationMinutes(doctor.getConsultationDurationMinutes())
                .availableDays(doctor.getAvailableDays())
                .build();
    }
}
