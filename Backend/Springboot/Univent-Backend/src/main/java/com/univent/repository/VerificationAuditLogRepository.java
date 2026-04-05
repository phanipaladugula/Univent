package com.univent.repository;

import com.univent.model.entity.VerificationAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VerificationAuditLogRepository extends JpaRepository<VerificationAuditLog, UUID> {
    List<VerificationAuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId);
}