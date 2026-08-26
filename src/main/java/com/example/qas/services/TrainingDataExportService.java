package com.example.qas.services;

import com.example.qas.models.Appointment;
import com.example.qas.models.enums.AppointmentStatus;
import com.example.qas.models.enums.NotificationStatus;
import com.example.qas.repositories.AppointmentRepository;
import com.example.qas.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TrainingDataExportService {
    private final AppointmentRepository appointmentRepository;
    private final NotificationRepository notificationRepository;

    public String exportCsv(LocalDate from, LocalDate to) {
        List<Appointment> appointments = appointmentRepository.findTrainingAppointments(from, to);
        StringBuilder csv = new StringBuilder("appointment_id,patient_id,doctor_id,department_id,hospital_id,appointment_date,appointment_hour,day_of_week,emergency,queue_position,estimated_wait_minutes,actual_wait_minutes,consultation_duration_minutes,reminder_sent,outcome,no_show\n");
        for (Appointment a : appointments) {
            boolean reminderSent = notificationRepository.existsByAppointmentIdAndSubjectAndStatus(
                    a.getId(), "Appointment Reminder", NotificationStatus.SENT);
            Long consultationDuration = a.getConsultationStartedAt() != null && a.getConsultationEndedAt() != null
                    ? Duration.between(a.getConsultationStartedAt(), a.getConsultationEndedAt()).toMinutes() : null;
            csv.append(a.getId()).append(',').append(a.getPatient().getId()).append(',')
                    .append(a.getDoctor() == null ? "" : a.getDoctor().getId()).append(',')
                    .append(a.getDepartment().getId()).append(',').append(a.getDepartment().getHospital().getId()).append(',')
                    .append(a.getRequestedDate()).append(',').append(a.getRequestedTime().getHour()).append(',')
                    .append(a.getRequestedDate().getDayOfWeek()).append(',').append(a.getEmergencyFlag()).append(',')
                    .append(value(a.getQueuePosition())).append(',').append(value(a.getEstimatedWaitTimeMinutes())).append(',')
                    .append(value(a.getActualWaitTimeMinutes())).append(',').append(value(consultationDuration)).append(',')
                    .append(reminderSent).append(',').append(a.getStatus()).append(',')
                    .append(a.getStatus() == AppointmentStatus.NO_SHOW).append('\n');
        }
        return csv.toString();
    }

    private Object value(Object value) {
        return value == null ? "" : value;
    }
}
