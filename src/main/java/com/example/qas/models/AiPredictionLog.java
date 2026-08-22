package com.example.qas.models;

import com.example.qas.models.enums.PredictionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "ai_prediction_logs")
public class AiPredictionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @NotNull
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "prediction_type", nullable = false, columnDefinition = "prediction_type")
    private PredictionType predictionType;

    @NotNull
    @Column(name = "input_features", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> inputFeatures;

    @NotNull
    @Column(name = "prediction_result", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> predictionResult;

    @Column(name = "actual_outcome")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> actualOutcome;

    @Size(max = 50)
    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}