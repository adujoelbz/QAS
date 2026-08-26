package com.example.qas.services;

import com.example.qas.models.Appointment;
import com.example.qas.models.Notification;
import com.example.qas.models.Patient;
import com.example.qas.models.User;
import com.example.qas.models.enums.NotificationStatus;
import com.example.qas.models.enums.NotificationType;
import com.example.qas.repositories.AppointmentRepository;
import com.example.qas.repositories.NotificationRepository;
import com.example.qas.services.notification.EmailProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final AppointmentRepository appointmentRepository;
    private final EmailProvider emailProvider;

    @Value("${app.notification.base-url}")
    private String baseUrl;

    // === SEND NOTIFICATIONS ===

    @Transactional
    public void sendAppointmentApprovalNotification(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        Patient patient = appointment.getPatient();
        User user = patient.getUser();

        String subject = "Appointment Approved";
        String body = buildApprovalBody(appointment);
        String email = user.getEmail();

        sendEmailNotification(appointment, email, subject, body, NotificationType.EMAIL);
    }

    @Transactional
    public void sendAppointmentReminder(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        Patient patient = appointment.getPatient();
        User user = patient.getUser();

        String subject = "Appointment Reminder";
        String body = buildReminderBody(appointment);
        String email = user.getEmail();

        sendEmailNotification(appointment, email, subject, body, NotificationType.EMAIL);
    }

    @Transactional
    public void sendAppointmentCancellationNotification(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        Patient patient = appointment.getPatient();
        User user = patient.getUser();

        String subject = "Appointment Cancelled";
        String body = "Your appointment scheduled for " + appointment.getRequestedDate() +
                " at " + appointment.getRequestedTime() + " has been cancelled.";
        String email = user.getEmail();

        sendEmailNotification(appointment, email, subject, body, NotificationType.EMAIL);
    }

    @Transactional
    public void sendAppointmentRescheduleNotification(Long appointmentId, LocalDate oldDate, LocalTime oldTime) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        Patient patient = appointment.getPatient();
        User user = patient.getUser();

        String subject = "Appointment Rescheduled";
        String body = "Your appointment has been rescheduled from " + oldDate + " at " + oldTime +
                " to " + appointment.getRequestedDate() + " at " + appointment.getRequestedTime() + ".";
        String email = user.getEmail();

        sendEmailNotification(appointment, email, subject, body, NotificationType.EMAIL);
    }

    @Transactional
    public void sendNoShowAlert(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        Patient patient = appointment.getPatient();
        User user = patient.getUser();

        String subject = "No-Show Alert";
        String body = "You did not attend your appointment scheduled for " +
                appointment.getRequestedDate() + " at " + appointment.getRequestedTime() +
                ". Please contact the hospital to reschedule.";
        String email = user.getEmail();

        sendEmailNotification(appointment, email, subject, body, NotificationType.EMAIL);
    }

    @Transactional
    public void sendConfirmationRequest(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        Patient patient = appointment.getPatient();
        User user = patient.getUser();

        String subject = "Confirm Your Appointment";
        String confirmUrl = baseUrl + "/api/appointments/" + appointmentId + "/confirm";
        String body = "Please confirm your appointment on " + appointment.getRequestedDate() +
                " at " + appointment.getRequestedTime() + " by clicking: " + confirmUrl;
        String email = user.getEmail();

        sendEmailNotification(appointment, email, subject, body, NotificationType.EMAIL);
    }

    @Transactional
    public void retryFailedNotification(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (notification.getStatus() != NotificationStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only failed notifications can be retried");
        }
        try {
            emailProvider.sendEmail(notification.getRecipient(), notification.getSubject(),
                    notification.getContent(), false);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(OffsetDateTime.now());
            notification.setErrorMessage(null);
        } catch (Exception e) {
            notification.setErrorMessage(trimError(e.getMessage()));
        }
        notificationRepository.save(notification);
    }

    // === PRIVATE HELPERS ===

    private void sendEmailNotification(Appointment appointment, String recipient, String subject, String body, NotificationType type) {
        try {
            emailProvider.sendEmail(recipient, subject, body, false);
            saveNotification(appointment, recipient, type, subject, body, NotificationStatus.SENT);
        } catch (Exception e) {
            saveNotification(appointment, recipient, type, subject, body, NotificationStatus.FAILED, trimError(e.getMessage()));
        }
    }

    private String trimError(String message) {
        if (message == null || message.isBlank()) return "Notification delivery failed";
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }

    private void saveNotification(Appointment appointment, String recipient, NotificationType type,
                                  String subject, String content, NotificationStatus status) {
        Notification notification = new Notification();
        notification.setAppointment(appointment);
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setSubject(subject);
        notification.setContent(content);
        notification.setStatus(status);
        if (status == NotificationStatus.SENT) {
            notification.setSentAt(OffsetDateTime.now());
        }
        notificationRepository.save(notification);
    }

    private void saveNotification(Appointment appointment, String recipient, NotificationType type,
                                  String subject, String content, NotificationStatus status, String errorMessage) {
        Notification notification = new Notification();
        notification.setAppointment(appointment);
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setSubject(subject);
        notification.setContent(content);
        notification.setStatus(status);
        notification.setErrorMessage(errorMessage);
        if (status == NotificationStatus.SENT) {
            notification.setSentAt(OffsetDateTime.now());
        }
        notificationRepository.save(notification);
    }

    // === TEMPLATE BUILDERS ===

    private String buildApprovalBody(Appointment appointment) {
        return "Your appointment has been approved.\n\n" +
                "Details:\n" +
                "Date: " + appointment.getRequestedDate() + "\n" +
                "Time: " + appointment.getRequestedTime() + "\n" +
                "Department: " + appointment.getDepartment().getName() + "\n" +
                "Hospital: " + appointment.getDepartment().getHospital().getName() + "\n" +
                "Doctor: " + (appointment.getDoctor() != null ?
                appointment.getDoctor().getFirstName() + " " + appointment.getDoctor().getLastName() :
                "To be assigned") + "\n\n" +
                "Thank you for using Queueless.";
    }

    private String buildReminderBody(Appointment appointment) {
        return "Reminder: You have an appointment tomorrow.\n\n" +
                "Details:\n" +
                "Date: " + appointment.getRequestedDate() + "\n" +
                "Time: " + appointment.getRequestedTime() + "\n" +
                "Department: " + appointment.getDepartment().getName() + "\n" +
                "Hospital: " + appointment.getDepartment().getHospital().getName() + "\n" +
                "Please arrive 15 minutes early.";
    }
}
