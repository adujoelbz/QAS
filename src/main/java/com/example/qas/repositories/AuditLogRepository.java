package com.example.qas.repositories;

import com.example.qas.models.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByAdminId(Long adminId);
    List<AuditLog> findByCreatedAtBetween(OffsetDateTime start, OffsetDateTime end);
    List<AuditLog> findByActionContainingIgnoreCase(String action);
    Page<AuditLog> findByAdminId(Long adminId, Pageable pageable);
    Page<AuditLog> findByCreatedAtBetween(OffsetDateTime from, OffsetDateTime to, Pageable pageable);
}