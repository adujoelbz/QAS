package com.example.qas.services;

import com.example.qas.models.enums.AppointmentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppointmentTransitionPolicyTests {

    private final AppointmentTransitionPolicy policy = new AppointmentTransitionPolicy();

    @Test
    void allowsApprovedOrConfirmedAppointmentsToReachConsultationOutcomes() {
        assertDoesNotThrow(() -> policy.validateDoctorOutcome(
                AppointmentStatus.APPROVED, AppointmentStatus.COMPLETED));
        assertDoesNotThrow(() -> policy.validateDoctorOutcome(
                AppointmentStatus.CONFIRMED, AppointmentStatus.NO_SHOW));
    }

    @Test
    void rejectsAdministrativeAndPatientStatusesFromDoctorEndpoint() {
        assertThrows(ResponseStatusException.class, () -> policy.validateDoctorOutcome(
                AppointmentStatus.CONFIRMED, AppointmentStatus.CANCELLED));
        assertThrows(ResponseStatusException.class, () -> policy.validateDoctorOutcome(
                AppointmentStatus.CONFIRMED, AppointmentStatus.APPROVED));
    }

    @Test
    void rejectsTerminalOrPendingAppointments() {
        assertThrows(ResponseStatusException.class, () -> policy.validateDoctorOutcome(
                AppointmentStatus.PENDING, AppointmentStatus.COMPLETED));
        assertThrows(ResponseStatusException.class, () -> policy.validateDoctorOutcome(
                AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW));
    }
}
