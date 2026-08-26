package com.example.qas.services;

import com.example.qas.models.enums.AppointmentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AppointmentTransitionPolicy {

    public void validateDoctorOutcome(AppointmentStatus current, AppointmentStatus requested) {
        if (requested != AppointmentStatus.COMPLETED && requested != AppointmentStatus.NO_SHOW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Doctors may only mark appointments COMPLETED or NO_SHOW");
        }
        if (current != AppointmentStatus.APPROVED && current != AppointmentStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only approved or confirmed appointments can be completed");
        }
    }
}
