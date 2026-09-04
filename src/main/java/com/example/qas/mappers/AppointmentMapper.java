package com.example.qas.mappers;

import com.example.qas.models.Appointment;
import com.example.qas.dto.request.AppointmentRequest;
import com.example.qas.dto.response.AdminAppointmentResponse;
import com.example.qas.dto.response.AppointmentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AppointmentMapper {
    @Mapping(target = "patient", ignore = true)
    @Mapping(target = "department", ignore = true)
    @Mapping(target = "doctor", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "queuePosition", ignore = true)
    @Mapping(target = "estimatedWaitTimeMinutes", ignore = true)
    @Mapping(target = "actualWaitTimeMinutes", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "confirmedAt", ignore = true)
    @Mapping(target = "cancelledAt", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    Appointment toEntity(AppointmentRequest source);

    @Mapping(target = "patientId", source = "patient.id")
    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "doctorId", source = "doctor.id")
    @Mapping(target = "doctorName", expression = "java(source.getDoctor() == null ? null : source.getDoctor().getFirstName() + \" \" + source.getDoctor().getLastName())")
    @Mapping(target = "medicalHistory", source = "patient.medicalHistory")
    AppointmentResponse toResponse(Appointment source);

    @Mapping(target = "patientId", source = "patient.id")
    @Mapping(target = "patientName", expression = "java(source.getPatient().getFirstName() + \" \" + source.getPatient().getLastName())")
    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "departmentName", source = "department.name")
    @Mapping(target = "doctorId", source = "doctor.id")
    @Mapping(target = "doctorName", expression = "java(source.getDoctor() == null ? null : source.getDoctor().getFirstName() + \" \" + source.getDoctor().getLastName())")
    AdminAppointmentResponse toAdminResponse(Appointment source);
}
