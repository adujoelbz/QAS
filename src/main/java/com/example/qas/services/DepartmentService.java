package com.example.qas.services;

import com.example.qas.dto.request.DepartmentCreateRequest;
import com.example.qas.dto.response.DepartmentResponse;
import com.example.qas.mappers.DepartmentMapper;
import com.example.qas.models.Department;
import com.example.qas.models.Hospital;
import com.example.qas.repositories.DepartmentRepository;
import com.example.qas.repositories.HospitalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final HospitalRepository hospitalRepository;
    private final DepartmentMapper departmentMapper;

    @Transactional
    public DepartmentResponse createDepartment(DepartmentCreateRequest request, Long hospitalId) {
        Hospital hospital = hospitalRepository.findById(hospitalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hospital not found"));

        Department department = departmentMapper.toEntity(request);
        department.setHospital(hospital);
        department = departmentRepository.save(department);
        return departmentMapper.toResponse(department);
    }

    @Transactional
    public DepartmentResponse updateDepartment(Long id, DepartmentCreateRequest request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));

        department.setName(request.getName());
        department.setSpecialty(request.getSpecialty());
        department.setEstimatedConsultationDurationMinutes(request.getEstimatedConsultationDurationMinutes());

        department = departmentRepository.save(department);
        return departmentMapper.toResponse(department);
    }

    @Transactional
    public void deleteDepartment(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));
        departmentRepository.delete(department);
    }

    public DepartmentResponse getDepartmentById(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));
        return departmentMapper.toResponse(department);
    }

    public List<DepartmentResponse> getDepartmentsByHospital(Long hospitalId) {
        // Verify hospital exists
        if (!hospitalRepository.existsById(hospitalId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hospital not found");
        }
        return departmentRepository.findByHospitalId(hospitalId).stream()
                .map(departmentMapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<DepartmentResponse> getDepartmentsBySpecialty(String specialty) {
        return departmentRepository.findBySpecialty(specialty).stream()
                .map(departmentMapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<DepartmentResponse> getDepartmentsByHospitalAndSpecialty(Long hospitalId, String specialty) {
        if (!hospitalRepository.existsById(hospitalId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hospital not found");
        }
        return departmentRepository.findByHospitalIdAndSpecialty(hospitalId, specialty).stream()
                .map(departmentMapper::toResponse)
                .collect(Collectors.toList());
    }

    public List<String> getAllSpecialties() {
        return departmentRepository.findDistinctSpecialties();
    }
}