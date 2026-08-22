package com.example.qas.mappers;

import com.example.qas.models.Department;
import com.example.qas.dto.request.DepartmentCreateRequest;
import com.example.qas.dto.response.DepartmentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DepartmentMapper {
    @Mapping(target = "id", ignore = true) @Mapping(target = "hospital", ignore = true) @Mapping(target = "createdAt", ignore = true) @Mapping(target = "updatedAt", ignore = true)
    Department toEntity(DepartmentCreateRequest source);
    @Mapping(target = "hospitalId", source = "hospital.id")
    DepartmentResponse toResponse(Department source);
}
