package com.example.qas.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.qas.dto.request.PatientUpdateRequest;
import com.example.qas.dto.response.PatientProfileResponse;
import com.example.qas.mappers.PatientMapper;
import com.example.qas.models.Patient;
import com.example.qas.models.User;
import com.example.qas.repositories.PatientRepository;
import com.example.qas.repositories.UserRepository;
import com.example.qas.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png"
    );

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final PatientMapper patientMapper;
    private final Cloudinary cloudinary;

    public record FileResource(String url, String originalFilename) {}

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
        validateMedicalHistoryFile(file);
        String publicId = null;
        String resourceType = null;

        try {
            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "type", "private",
                    "resource_type", "auto",
                    "folder", "qas/medical-history/" + patient.getId(),
                    "use_filename", false,
                    "unique_filename", true
            );
            Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), uploadParams);

            publicId = Objects.toString(uploadResult.get("public_id"), null);
            resourceType = Objects.toString(uploadResult.get("resource_type"), "raw");
            String format = Objects.toString(uploadResult.get("format"), "");
            if (publicId == null) {
                throw new IOException("Cloudinary did not return a public ID");
            }

            Map<String, Object> medicalHistory = patient.getMedicalHistory();
            if (medicalHistory == null) {
                medicalHistory = new HashMap<>();
            } else {
                medicalHistory = new HashMap<>(medicalHistory);
            }

            Map<String, Object> fileEntry = new HashMap<>();
            fileEntry.put("publicId", publicId);
            fileEntry.put("resourceType", resourceType);
            fileEntry.put("format", format);
            fileEntry.put("originalFileName", safeFilename(file.getOriginalFilename()));
            fileEntry.put("fileType", file.getContentType());
            fileEntry.put("fileSize", file.getSize());
            fileEntry.put("uploadedAt", OffsetDateTime.now().toString());

            Object filesObj = medicalHistory.get("files");
            if (filesObj instanceof List) {
                List<Object> filesList = new ArrayList<>((List<?>) filesObj);
                filesList.add(fileEntry);
                medicalHistory.put("files", filesList);
            } else {
                List<Object> filesList = new ArrayList<>();
                filesList.add(fileEntry);
                medicalHistory.put("files", filesList);
            }

            patient.setMedicalHistory(medicalHistory);
            patientRepository.save(patient);

            return Map.of("message", "File uploaded successfully", "publicId", publicId);

        } catch (Exception e) {
            log.error("Medical history upload failed for patient {}", patient.getId(), e);
            deleteUploadedAssetQuietly(publicId, resourceType);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to upload medical history file", e);
        }
    }

    public FileResource getMedicalHistoryFileResource(String publicId) {
        // Verify the file belongs to the current patient
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
                .filter(f -> publicId.equals(f.get("publicId")))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found for this patient"));

        String signedUrl;
        try {
            String format = Objects.toString(fileMetadata.get("format"), "");
            String resourceType = Objects.toString(fileMetadata.get("resourceType"), "raw");
            signedUrl = cloudinary.privateDownload(publicId, format, ObjectUtils.asMap(
                    "resource_type", resourceType,
                    "expires_at", System.currentTimeMillis() / 1000 + 300,
                    "attachment", true
            ));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate download URL");
        }

        String originalFilename = (String) fileMetadata.get("originalFileName");
        return new FileResource(signedUrl, originalFilename);
    }

    private void validateMedicalHistoryFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the 10 MB limit");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PDF, JPEG, and PNG files are allowed");
        }
    }

    private String safeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "medical-history-file";
        }
        return originalFilename.replace('\\', '/').substring(originalFilename.replace('\\', '/').lastIndexOf('/') + 1);
    }

    private void deleteUploadedAssetQuietly(String publicId, String resourceType) {
        if (publicId == null) {
            return;
        }
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap(
                    "resource_type", resourceType == null ? "raw" : resourceType,
                    "type", "private",
                    "invalidate", true
            ));
        } catch (Exception ignored) {
            // Preserve the original upload or persistence error.
        }
    }

    private Patient getCurrentPatient() {
        User currentUser = SecurityUtils.getCurrentUser(userRepository);
        return patientRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient profile not found"));
    }
}
