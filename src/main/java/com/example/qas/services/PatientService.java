package com.example.qas.services;

import com.example.qas.dto.request.PatientUpdateRequest;
import com.example.qas.dto.response.PatientProfileResponse;
import com.example.qas.mappers.PatientMapper;
import com.example.qas.models.Patient;
import com.example.qas.models.User;
import com.example.qas.repositories.PatientRepository;
import com.example.qas.repositories.UserRepository;
import com.example.qas.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final PatientMapper patientMapper;

    private static final String UPLOAD_DIR = "uploads/medical-history/";

    public record FileResource(Resource resource, String originalFilename) {}

    public PatientProfileResponse getCurrentPatientProfile() {
        Patient patient = getCurrentPatient();
        return patientMapper.toProfileResponse(patient);
    }

    @Transactional
    public PatientProfileResponse updateCurrentPatientProfile(PatientUpdateRequest request) {
        Patient patient = getCurrentPatient();
        patientMapper.update(patient, request);
        patient = patientRepository.save(patient);
        return patientMapper.toProfileResponse(patient);
    }

    @Transactional
    public Map<String, String> uploadMedicalHistory(MultipartFile file) {
        Patient patient = getCurrentPatient();

        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create upload directory");
        }

        String originalFilename = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String fileId = UUID.randomUUID().toString();
        String storedFileName = fileId + fileExtension;
        Path filePath = Paths.get(UPLOAD_DIR, storedFileName);

        try {
            Files.write(filePath, file.getBytes());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save file");
        }

        Map<String, Object> medicalHistory = patient.getMedicalHistory();
        if (medicalHistory == null) {
            medicalHistory = new HashMap<>();
        }

        Map<String, Object> fileEntry = new HashMap<>();
        fileEntry.put("fileId", fileId);
        fileEntry.put("fileName", originalFilename);
        fileEntry.put("storedFileName", storedFileName);
        fileEntry.put("fileType", file.getContentType());
        fileEntry.put("fileSize", file.getSize());
        fileEntry.put("uploadedAt", LocalDateTime.now().toString());

        Object filesObj = medicalHistory.get("files");
        if (filesObj instanceof List) {
            ((List<Object>) filesObj).add(fileEntry);
        } else {
            List<Object> filesList = new ArrayList<>();
            filesList.add(fileEntry);
            medicalHistory.put("files", filesList);
        }

        patient.setMedicalHistory(medicalHistory);
        patientRepository.save(patient);

        return Map.of("message", "File uploaded successfully", "fileId", fileId);
    }

    public FileResource getMedicalHistoryFileResource(String fileId) {
        Patient patient = getCurrentPatient();
        Map<String, Object> medicalHistory = patient.getMedicalHistory();
        if (medicalHistory == null || !medicalHistory.containsKey("files")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No medical history files found");
        }

        Object filesObj = medicalHistory.get("files");
        if (!(filesObj instanceof List)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid file list");
        }

        List<Map<String, Object>> files = (List<Map<String, Object>>) filesObj;
        Map<String, Object> fileMetadata = files.stream()
                .filter(f -> fileId.equals(f.get("fileId")))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found for this patient"));

        String storedFileName = (String) fileMetadata.get("storedFileName");
        if (storedFileName == null) {
            // fallback: look for file by prefix (old style)
            Path uploadPath = Paths.get(UPLOAD_DIR);
            File directory = uploadPath.toFile();
            File[] matchingFiles = directory.listFiles((dir, name) -> name.startsWith(fileId));
            if (matchingFiles != null && matchingFiles.length > 0) {
                storedFileName = matchingFiles[0].getName();
            } else {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found on disk");
            }
        }

        Path filePath = Paths.get(UPLOAD_DIR, storedFileName);
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                String originalFilename = (String) fileMetadata.get("fileName");
                return new FileResource(resource, originalFilename);
            } else {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not readable");
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading file");
        }
    }

    private Patient getCurrentPatient() {
        User currentUser = SecurityUtils.getCurrentUser(userRepository);
        return patientRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient profile not found"));
    }
}