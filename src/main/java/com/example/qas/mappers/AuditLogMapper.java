package com.example.qas.mappers;

import com.example.qas.models.AuditLog;
import com.example.qas.dto.response.AuditLogResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuditLogMapper {
    @Mapping(target = "adminId", source = "admin.id")
    AuditLogResponse toResponse(AuditLog source);
}
