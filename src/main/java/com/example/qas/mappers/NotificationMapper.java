package com.example.qas.mappers;

import com.example.qas.models.Notification;
import com.example.qas.dto.request.NotificationRequest;
import com.example.qas.dto.response.NotificationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "appointment", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "sentAt", ignore = true)
    @Mapping(target = "errorMessage", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Notification toEntity(NotificationRequest source);

    @Mapping(target = "appointmentId", source = "appointment.id")
    NotificationResponse toResponse(Notification source);
}
