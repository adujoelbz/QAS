package com.example.qas.mappers;

import com.example.qas.models.Hospital;
import com.example.qas.dto.request.HospitalCreateRequest;
import com.example.qas.dto.response.HospitalResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface HospitalMapper {
    @Mapping(target = "id", ignore = true) @Mapping(target = "createdAt", ignore = true) @Mapping(target = "updatedAt", ignore = true)
    Hospital toEntity(HospitalCreateRequest source);
    HospitalResponse toResponse(Hospital source);
}
