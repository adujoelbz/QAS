package com.example.qas.services;

import com.example.qas.dto.request.AdminCreateRequest;
import com.example.qas.dto.request.DoctorCreateRequest;
import com.example.qas.dto.response.ProvisionedAccountResponse;
import com.example.qas.models.Doctor;
import com.example.qas.models.Hospital;
import com.example.qas.models.User;
import com.example.qas.models.enums.UserRole;
import com.example.qas.repositories.DoctorRepository;
import com.example.qas.repositories.HospitalRepository;
import com.example.qas.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AccountProvisioningService {
    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final HospitalRepository hospitalRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public ProvisionedAccountResponse createAdmin(AdminCreateRequest request) {
        User user = createUser(request.getEmail(), request.getPassword(), UserRole.ADMIN);
        return ProvisionedAccountResponse.builder().userId(user.getId()).email(user.getEmail()).role(user.getRole().name()).enabled(user.getEnabled()).build();
    }

    @Transactional
    public ProvisionedAccountResponse createDoctor(DoctorCreateRequest request) {
        Hospital hospital = hospitalRepository.findById(request.getHospitalId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hospital not found"));
        User user = createUser(request.getEmail(), request.getPassword(), UserRole.DOCTOR);
        Doctor doctor = new Doctor();
        doctor.setUser(user);
        doctor.setFirstName(request.getFirstName().trim());
        doctor.setLastName(request.getLastName().trim());
        doctor.setSpecialty(request.getSpecialty().trim());
        doctor.setHospital(hospital);
        doctor.setConsultationDurationMinutes(request.getConsultationDurationMinutes() == null ? 30 : request.getConsultationDurationMinutes());
        doctor.setAvailableDays(request.getAvailableDays() == null ? Map.of() : request.getAvailableDays());
        doctor = doctorRepository.save(doctor);
        return ProvisionedAccountResponse.builder().userId(user.getId()).profileId(doctor.getId()).email(user.getEmail()).role(user.getRole().name()).enabled(user.getEnabled()).build();
    }

    private User createUser(String emailValue, String password, UserRole role) {
        String email = emailValue.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setEnabled(true);
        user.setLocked(false);
        return userRepository.save(user);
    }
}
