package com.example.qas.repositories;

import com.example.qas.models.AiPredictionLog;
import com.example.qas.models.enums.PredictionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface AiPredictionLogRepository extends JpaRepository<AiPredictionLog, Long> {
    List<AiPredictionLog> findByAppointmentId(Long appointmentId);
    List<AiPredictionLog> findByPredictionType(PredictionType predictionType);
    List<AiPredictionLog> findByAppointmentIdAndPredictionType(Long appointmentId, PredictionType predictionType);
    List<AiPredictionLog> findByModelVersion(String modelVersion);
    List<AiPredictionLog> findByPredictionTypeAndCreatedAtBetween(PredictionType type, OffsetDateTime from, OffsetDateTime to);
}