package com.example.qas.mappers;

import com.example.qas.models.AiPredictionLog;
import com.example.qas.dto.response.PredictionReport;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AiPredictionLogMapper {
    @Mapping(target = "appointmentId", source = "appointment.id")
    @Mapping(target = "predictionType", source = "predictionType")
    PredictionReport toResponse(AiPredictionLog source);
}
