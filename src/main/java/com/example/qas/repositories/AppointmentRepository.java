package com.example.qas.repositories;

import com.example.qas.models.Appointment;
import com.example.qas.models.enums.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByPatientId(Long patientId);
    List<Appointment> findByDoctorId(Long doctorId);
    List<Appointment> findByDepartmentId(Long departmentId);
    List<Appointment> findByStatus(AppointmentStatus status);
    List<Appointment> findByPatientIdAndStatus(Long patientId, AppointmentStatus status);
    List<Appointment> findByDoctorIdAndStatus(Long doctorId, AppointmentStatus status);
    List<Appointment> findByDepartmentIdAndStatus(Long departmentId, AppointmentStatus status);

    // For queue ordering – active appointments (approved/confirmed)
    @Query("SELECT a FROM Appointment a WHERE a.department.id = :departmentId " +
            "AND a.status IN ('APPROVED', 'CONFIRMED') " +
            "ORDER BY a.emergencyFlag DESC, a.requestedDate, a.requestedTime")
    List<Appointment> findActiveQueueByDepartment(@Param("departmentId") Long departmentId);

    // For checking overlapping appointments (for a doctor on a given date/time)
    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId " +
            "AND a.requestedDate = :date " +
            "AND a.requestedTime BETWEEN :startTime AND :endTime " +
            "AND a.status IN ('APPROVED', 'CONFIRMED')")
    List<Appointment> findConflictingAppointments(@Param("doctorId") Long doctorId,
                                                  @Param("date") LocalDate date,
                                                  @Param("startTime") LocalTime startTime,
                                                  @Param("endTime") LocalTime endTime);

    // For a patient, all appointments in date range
    List<Appointment> findByPatientIdAndRequestedDateBetween(Long patientId, LocalDate start, LocalDate end);

    // For department statistics
    long countByDepartmentIdAndStatus(Long departmentId, AppointmentStatus status);
}