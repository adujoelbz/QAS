package com.example.qas.repositories;

import com.example.qas.models.Appointment;
import com.example.qas.models.enums.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {
    // === By user role ===
    List<Appointment> findByPatientId(Long patientId);
    Page<Appointment> findByPatientId(Long patientId, Pageable pageable);
    List<Appointment> findByDoctorId(Long doctorId);
    List<Appointment> findByDepartmentId(Long departmentId);

    // === By status ===
    List<Appointment> findByStatus(AppointmentStatus status);
    List<Appointment> findByPatientIdAndStatus(Long patientId, AppointmentStatus status);
    long countByPatientIdAndStatus(Long patientId, AppointmentStatus status);
    // NEW: with Pageable support
    Page<Appointment> findByPatientIdAndStatus(Long patientId, AppointmentStatus status, Pageable pageable);
    List<Appointment> findByDoctorIdAndStatus(Long doctorId, AppointmentStatus status);
    List<Appointment> findByDepartmentIdAndStatus(Long departmentId, AppointmentStatus status);

    // === By date ===
    List<Appointment> findByDoctorIdAndRequestedDateBetween(Long doctorId, LocalDate start, LocalDate end);
    List<Appointment> findByDoctorIdAndStatusAndRequestedDate(Long doctorId, AppointmentStatus status, LocalDate date);
    List<Appointment> findByDoctorIdAndRequestedDate(Long doctorId, LocalDate date);
    List<Appointment> findByPatientIdAndRequestedDateBetween(Long patientId, LocalDate start, LocalDate end);

    // === For admin dashboard ===
    long countByStatus(AppointmentStatus status);
    long countByRequestedDate(LocalDate date);
    long countByRequestedDateBetween(LocalDate start, LocalDate end);
    long countByStatusAndRequestedDate(AppointmentStatus status, LocalDate date);
    long countByDepartmentIdAndStatus(Long departmentId, AppointmentStatus status);

    // === For queue management ===
    @Query("SELECT a FROM Appointment a WHERE a.department.id = :departmentId " +
            "AND a.status IN ('APPROVED', 'CONFIRMED') " +
            "ORDER BY a.emergencyFlag DESC, a.requestedDate ASC, a.requestedTime ASC")
    List<Appointment> findActiveQueueByDepartment(@Param("departmentId") Long departmentId);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.department.id = :departmentId " +
            "AND a.status IN ('APPROVED', 'CONFIRMED') " +
            "AND ((a.emergencyFlag = false AND a.requestedDate < :date) OR " +
            "(a.emergencyFlag = false AND a.requestedDate = :date AND a.requestedTime <= :time))")
    int countPatientsAheadInQueue(@Param("departmentId") Long departmentId,
                                  @Param("date") LocalDate date,
                                  @Param("time") LocalTime time);

    // === For slot availability ===
    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId " +
            "AND a.requestedDate = :date " +
            "AND a.requestedTime >= :startTime " +
            "AND a.requestedTime < :endTime " +
            "AND a.status IN ('APPROVED', 'CONFIRMED')")
    List<Appointment> findConflictingAppointments(@Param("doctorId") Long doctorId,
                                                  @Param("date") LocalDate date,
                                                  @Param("startTime") LocalTime startTime,
                                                  @Param("endTime") LocalTime endTime);

    // === For standby ===
    @Query("SELECT a FROM Appointment a WHERE a.department.id = :departmentId " +
            "AND a.status = 'PENDING' AND a.emergencyFlag = false " +
            "ORDER BY a.createdAt ASC")
    List<Appointment> findPendingAppointmentsForStandby(@Param("departmentId") Long departmentId);

    @Query("SELECT a FROM Appointment a WHERE a.department.id = :departmentId " +
            "AND a.status = 'PENDING' AND a.standbyRequested = true " +
            "ORDER BY a.emergencyFlag DESC, a.createdAt ASC")
    List<Appointment> findStandbyCandidates(@Param("departmentId") Long departmentId);

    // === Existing methods ===
    Optional<Appointment> findByIdAndPatientId(Long id, Long patientId);

    List<Appointment> findByDepartmentIdAndStatusIn(Long departmentId, List<AppointmentStatus> statuses);


    long countByStatusAndRequestedDateBetween(AppointmentStatus status, LocalDate start, LocalDate end);
    @Query("SELECT AVG(a.actualWaitTimeMinutes) FROM Appointment a WHERE a.status = 'COMPLETED' AND a.requestedDate BETWEEN :start AND :end")
    Double findAverageActualWaitTime(@Param("start") LocalDate start, @Param("end") LocalDate end);
    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.status = 'NO_SHOW' AND a.requestedDate BETWEEN :start AND :end")
    long countNoShowsBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
    List<Appointment> findByRequestedDateAndStatus(LocalDate date, AppointmentStatus status);

    @Query("SELECT a FROM Appointment a JOIN FETCH a.patient JOIN FETCH a.department d JOIN FETCH d.hospital " +
            "LEFT JOIN FETCH a.doctor WHERE a.requestedDate BETWEEN :from AND :to " +
            "AND a.status IN ('COMPLETED', 'NO_SHOW', 'CANCELLED') ORDER BY a.requestedDate, a.requestedTime")
    List<Appointment> findTrainingAppointments(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
