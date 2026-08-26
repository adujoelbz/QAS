package com.example.qas.repositories;

import com.example.qas.models.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByAppointmentId(Long appointmentId);
    List<Question> findByPatientId(Long patientId);
    List<Question> findByDoctorId(Long doctorId);
    Optional<Question> findByIdAndAppointmentId(Long id, Long appointmentId);
}