package com.example.qas.controllers;

import com.example.qas.dto.request.PatientUpdateRequest;
import com.example.qas.dto.response.PatientProfileResponse;
import com.example.qas.services.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PATIENT')")
public class PatientController {

    private final PatientService patientService;

    @GetMapping("/me")
    public ResponseEntity<PatientProfileResponse> getMyProfile() {
        PatientProfileResponse response = patientService.getCurrentPatientProfile();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me")
    public ResponseEntity<PatientProfileResponse> updateMyProfile(@Valid @RequestBody PatientUpdateRequest request) {
        PatientProfileResponse response = patientService.updateCurrentPatientProfile(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/me/medical-history", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadMedicalHistory(@RequestParam("file") MultipartFile file) {
        Map<String, String> response = patientService.uploadMedicalHistory(file);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me/medical-history/{fileId}")
    public ResponseEntity<Resource> downloadMedicalHistoryFile(@PathVariable String fileId) {
        PatientService.FileResource fileResource = patientService.getMedicalHistoryFileResource(fileId);
        Resource resource = fileResource.resource();
        String originalFilename = fileResource.originalFilename();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + originalFilename + "\"")
                .body(resource);
    }
}