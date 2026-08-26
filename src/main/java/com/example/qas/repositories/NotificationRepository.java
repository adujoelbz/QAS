package com.example.qas.repositories;

import com.example.qas.models.Notification;
import com.example.qas.models.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByAppointmentId(Long appointmentId);
    List<Notification> findByStatus(NotificationStatus status);
    List<Notification> findByAppointmentIdAndStatus(Long appointmentId, NotificationStatus status);
    boolean existsByAppointmentIdAndSubjectAndStatus(Long appointmentId, String subject, NotificationStatus status);
}
