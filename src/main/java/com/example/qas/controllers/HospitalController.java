package com.example.qas.controllers;

import com.example.qas.dto.request.HospitalCreateRequest;
import com.example.qas.dto.response.HospitalResponse;
import com.example.qas.services.HospitalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HospitalController {

    private final HospitalService hospitalService;

    // === PUBLIC ENDPOINTS ===

    @GetMapping("/public/hospitals")
    public ResponseEntity<Page<HospitalResponse>> searchHospitals(
            @RequestParam(required = false) String name,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(hospitalService.searchHospitals(name, pageable));
    }

    @GetMapping("/public/hospitals/nearby")
    public ResponseEntity<Page<HospitalResponse>> findNearbyHospitals(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(defaultValue = "10") Double radius,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(hospitalService.findNearbyHospitals(lat, lng, radius, pageable));
    }

    @GetMapping("/public/hospitals/{id}")
    public ResponseEntity<HospitalResponse> getHospitalById(@PathVariable Long id) {
        return ResponseEntity.ok(hospitalService.getHospitalById(id));
    }

    // === ADMIN ENDPOINTS ===

    @PostMapping("/admin/hospitals")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HospitalResponse> createHospital(@Valid @RequestBody HospitalCreateRequest request) {
        HospitalResponse response = hospitalService.createHospital(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/admin/hospitals/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HospitalResponse> updateHospital(
            @PathVariable Long id,
            @Valid @RequestBody HospitalCreateRequest request) {
        return ResponseEntity.ok(hospitalService.updateHospital(id, request));
    }

    @DeleteMapping("/admin/hospitals/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteHospital(@PathVariable Long id) {
        hospitalService.deleteHospital(id);
        return ResponseEntity.noContent().build();
    }
}