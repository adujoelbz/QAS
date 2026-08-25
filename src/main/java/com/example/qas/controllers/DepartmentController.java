package com.example.qas.controllers;

import com.example.qas.dto.request.DepartmentCreateRequest;
import com.example.qas.dto.response.DepartmentResponse;
import com.example.qas.services.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    // === PUBLIC ENDPOINTS ===

    @GetMapping("/public/departments")
    public ResponseEntity<List<DepartmentResponse>> getDepartments(
            @RequestParam(required = false) Long hospitalId,
            @RequestParam(required = false) String specialty) {
        if (hospitalId != null && specialty != null) {
            return ResponseEntity.ok(departmentService.getDepartmentsByHospitalAndSpecialty(hospitalId, specialty));
        } else if (hospitalId != null) {
            return ResponseEntity.ok(departmentService.getDepartmentsByHospital(hospitalId));
        } else if (specialty != null) {
            return ResponseEntity.ok(departmentService.getDepartmentsBySpecialty(specialty));
        } else {
            // Return all? Maybe not – require at least one filter
            throw new IllegalArgumentException("At least one of 'hospitalId' or 'specialty' is required");
        }
    }

    @GetMapping("/public/departments/{id}")
    public ResponseEntity<DepartmentResponse> getDepartmentById(@PathVariable Long id) {
        return ResponseEntity.ok(departmentService.getDepartmentById(id));
    }

    @GetMapping("/public/specialties")
    public ResponseEntity<List<String>> getAllSpecialties() {
        return ResponseEntity.ok(departmentService.getAllSpecialties());
    }

    // === ADMIN ENDPOINTS ===

    @PostMapping("/admin/hospitals/{hospitalId}/departments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DepartmentResponse> createDepartment(
            @PathVariable Long hospitalId,
            @Valid @RequestBody DepartmentCreateRequest request) {
        DepartmentResponse response = departmentService.createDepartment(request, hospitalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/admin/departments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DepartmentResponse> updateDepartment(
            @PathVariable Long id,
            @Valid @RequestBody DepartmentCreateRequest request) {
        return ResponseEntity.ok(departmentService.updateDepartment(id, request));
    }

    @DeleteMapping("/admin/departments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteDepartment(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return ResponseEntity.noContent().build();
    }
}