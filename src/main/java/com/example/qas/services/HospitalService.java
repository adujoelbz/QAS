package com.example.qas.services;

import com.example.qas.dto.request.HospitalCreateRequest;
import com.example.qas.dto.response.HospitalResponse;
import com.example.qas.mappers.HospitalMapper;
import com.example.qas.models.Hospital;
import com.example.qas.repositories.HospitalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class HospitalService {

    private final HospitalRepository hospitalRepository;
    private final HospitalMapper hospitalMapper;

    @Transactional
    public HospitalResponse createHospital(HospitalCreateRequest request) {
        Hospital hospital = hospitalMapper.toEntity(request);
        hospital = hospitalRepository.save(hospital);
        return hospitalMapper.toResponse(hospital);
    }

    @Transactional
    public HospitalResponse updateHospital(Long id, HospitalCreateRequest request) {
        Hospital hospital = hospitalRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hospital not found"));

        hospital.setName(request.getName());
        hospital.setAddress(request.getAddress());
        hospital.setLatitude(request.getLatitude());
        hospital.setLongitude(request.getLongitude());
        hospital.setPhone(request.getPhone());
        hospital.setEmail(request.getEmail());

        hospital = hospitalRepository.save(hospital);
        return hospitalMapper.toResponse(hospital);
    }

    @Transactional
    public void deleteHospital(Long id) {
        Hospital hospital = hospitalRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hospital not found"));
        hospitalRepository.delete(hospital);
    }

    public HospitalResponse getHospitalById(Long id) {
        Hospital hospital = hospitalRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hospital not found"));
        return hospitalMapper.toResponse(hospital);
    }

    public Page<HospitalResponse> searchHospitals(String name, Pageable pageable) {
        if (name != null && !name.isBlank()) {
            return hospitalRepository.findByNameContainingIgnoreCase(name, pageable)
                    .map(hospitalMapper::toResponse);
        }
        return hospitalRepository.findAll(pageable)
                .map(hospitalMapper::toResponse);
    }

    public Page<HospitalResponse> findNearbyHospitals(Double lat, Double lng, Double radiusKm, Pageable pageable) {
        // Use the repository method with Haversine formula (if implemented)
        return hospitalRepository.findNearbyHospitals(lat, lng, radiusKm, pageable)
                .map(hospitalMapper::toResponse);
    }
}