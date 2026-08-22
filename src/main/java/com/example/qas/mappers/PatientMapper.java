package com.example.qas.mappers;

import com.example.qas.models.Patient;
import com.example.qas.dto.request.PatientUpdateRequest;
import com.example.qas.dto.response.AdminPatientResponse;
import com.example.qas.dto.response.PatientProfileResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PatientMapper {
    @Mapping(target = "user", ignore = true) @Mapping(target = "id", ignore = true) @Mapping(target = "lastName", ignore = true) @Mapping(target = "dateOfBirth", ignore = true) @Mapping(target = "gender", ignore = true) @Mapping(target = "medicalHistory", ignore = true) @Mapping(target = "createdAt", ignore = true) @Mapping(target = "updatedAt", ignore = true)
    void update(@MappingTarget Patient target, PatientUpdateRequest source);
    @Mapping(target = "email", source = "user.email")
    PatientProfileResponse toProfileResponse(Patient source);
    @Mapping(target = "email", source = "user.email") @Mapping(target = "enabled", source = "user.enabled")
    AdminPatientResponse toAdminResponse(Patient source);
}
