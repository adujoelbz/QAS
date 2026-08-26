package com.example.qas.services;

import com.example.qas.dto.response.QueueAnalyticsResponse;
import com.example.qas.dto.response.QueueStatusResponse;
import com.example.qas.models.Appointment;
import com.example.qas.models.Department;
import com.example.qas.models.enums.AppointmentStatus;
import com.example.qas.repositories.AppointmentRepository;
import com.example.qas.repositories.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QueueService {

    private final AppointmentRepository appointmentRepository;
    private final DepartmentRepository departmentRepository;  // Added this

    // === Queue status for a specific appointment ===

    public QueueStatusResponse getQueueStatusForAppointment(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        // Only active appointments have queue status
        if (appointment.getStatus() != AppointmentStatus.APPROVED &&
                appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Queue status is only available for approved or confirmed appointments");
        }

        // Get active queue for the department
        List<Appointment> activeQueue = appointmentRepository.findActiveQueueByDepartment(
                appointment.getDepartment().getId());

        // Find position (1-based)
        int position = 0;
        for (int i = 0; i < activeQueue.size(); i++) {
            if (activeQueue.get(i).getId().equals(appointmentId)) {
                position = i + 1;
                break;
            }
        }

        if (position == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Appointment not found in active queue");
        }

        // Calculate patients ahead (position - 1)
        int patientsAhead = position - 1;

        // Estimate wait time using department's average duration
        int avgDuration = appointment.getDepartment().getEstimatedConsultationDurationMinutes();
        int estimatedWaitMinutes = patientsAhead * avgDuration;

        return QueueStatusResponse.builder()
                .appointmentId(appointmentId)
                .queuePosition(position)
                .patientsAhead(patientsAhead)
                .estimatedWaitMinutes(estimatedWaitMinutes)
                .status(appointment.getStatus())
                .build();
    }

    // === Recalculate queue positions for a department ===

    @Transactional
    public void recalcQueuePositionsForDepartment(Long departmentId) {
        List<Appointment> activeQueue = appointmentRepository.findActiveQueueByDepartment(departmentId);

        int pos = 1;
        for (Appointment app : activeQueue) {
            app.setQueuePosition(pos);
            pos++;
        }
        appointmentRepository.saveAll(activeQueue);
    }

    // === Trigger recalc after any change that affects queue order ===

    @Transactional
    public void handleQueueChange(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        recalcQueuePositionsForDepartment(appointment.getDepartment().getId());
    }

    // === Standby allocation ===

    @Transactional
    public void allocateStandbySlot(Long departmentId) {
        List<Appointment> pending = appointmentRepository.findPendingAppointmentsForStandby(departmentId);

        if (pending.isEmpty()) {
            return;
        }

        Appointment standbyCandidate = pending.get(0);
        System.out.println("Standby: Candidate appointment " + standbyCandidate.getId() + " could be assigned.");
    }

    // === Queue analytics ===

    public QueueAnalyticsResponse getQueueAnalytics(Long departmentId) {
        List<Appointment> activeQueue = appointmentRepository.findActiveQueueByDepartment(departmentId);

        // Get department name and duration
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));

        int queueLength = activeQueue.size();
        if (queueLength == 0) {
            return QueueAnalyticsResponse.builder()
                    .departmentName(department.getName())
                    .currentQueueLength(0)
                    .averageWaitMinutes(0.0)
                    .build();
        }

        int avgDuration = department.getEstimatedConsultationDurationMinutes();
        double avgWait = (queueLength * avgDuration) / 2.0;

        return QueueAnalyticsResponse.builder()
                .departmentName(department.getName())
                .currentQueueLength(queueLength)
                .averageWaitMinutes(avgWait)
                .build();
    }

    // === Emergency insertion handling ===

    @Transactional
    public void handleEmergencyAppointment(Long appointmentId) {
        handleQueueChange(appointmentId);
    }

    // === Batch update for all departments (e.g., scheduled job) ===

    @Transactional
    public void recalcAllQueuePositions() {
        // Placeholder – in production, you might run this periodically
    }
}