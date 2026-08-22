package com.example.qas.mappers;

import com.example.qas.models.Doctor;
import com.example.qas.dto.response.AdminDoctorResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DoctorMapper {
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "hospitalId", source = "hospital.id")
    @Mapping(target = "enabled", source = "user.enabled")
    AdminDoctorResponse toAdminResponse(Doctor source);
}
