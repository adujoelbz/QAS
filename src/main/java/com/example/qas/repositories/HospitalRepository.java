package com.example.qas.repositories;

import com.example.qas.models.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HospitalRepository extends JpaRepository<Hospital, Long> {
    List<Hospital> findByNameContainingIgnoreCase(String name);
    // Optionally add geolocation-based search (using JPQL with distance formula)
}