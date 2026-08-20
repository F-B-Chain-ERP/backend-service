package com.erp.backend_service.repository;

import com.erp.core.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Truy vấn dữ liệu lịch sử hệ thống (AuditLog). */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
