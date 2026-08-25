package com.example.qas.repositories;

import com.example.qas.models.Hospital;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HospitalRepository extends JpaRepository<Hospital, Long> {
    List<Hospital> findByNameContainingIgnoreCase(String name);
    // Optionally add geolocation-based search (using JPQL with distance formula)

    // New method: returns Page, takes Pageable
    Page<Hospital> findByNameContainingIgnoreCase(String name, Pageable pageable);

    // In HospitalRepository
    @Query("SELECT h FROM Hospital h WHERE " +
            "(6371 * acos(cos(radians(:lat)) * cos(radians(h.latitude)) * " +
            "cos(radians(h.longitude) - radians(:lng)) + sin(radians(:lat)) * sin(radians(h.latitude)))) < :radiusKm")
    Page<Hospital> findNearbyHospitals(@Param("lat") Double lat,
                                       @Param("lng") Double lng,
                                       @Param("radiusKm") Double radiusKm,
                                       Pageable pageable);
}