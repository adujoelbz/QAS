package com.example.qas.repositories;

import com.example.qas.models.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    Optional<Doctor> findByUserId(Long userId);
    List<Doctor> findByHospitalId(Long hospitalId);
    List<Doctor> findBySpecialty(String specialty);
    List<Doctor> findBySpecialtyAndHospitalId(String specialty, Long hospitalId);
}