package com.erp.backend_service.repository;

import com.erp.core.domain.BranchVariantStockLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BranchVariantStockLogRepository extends JpaRepository<BranchVariantStockLog, UUID> {
}
