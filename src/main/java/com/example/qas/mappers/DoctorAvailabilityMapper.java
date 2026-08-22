package com.example.qas.mappers;

import com.example.qas.models.DoctorAvailability;
import com.example.qas.dto.request.DoctorAvailabilityRequest;
import com.example.qas.dto.response.DoctorAvailabilityResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DoctorAvailabilityMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "doctor", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    DoctorAvailability toEntity(DoctorAvailabilityRequest source);

    @Mapping(target = "doctorId", source = "doctor.id")
    DoctorAvailabilityResponse toResponse(DoctorAvailability source);
}
