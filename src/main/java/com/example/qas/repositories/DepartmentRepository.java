package com.example.qas.repositories;

import com.example.qas.models.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findByHospitalId(Long hospitalId);
    List<Department> findBySpecialty(String specialty);
    List<Department> findByHospitalIdAndSpecialty(Long hospitalId, String specialty);
    List<Department> findByNameContainingIgnoreCase(String name);
}