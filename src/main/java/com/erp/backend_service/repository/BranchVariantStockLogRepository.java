package com.erp.backend_service.repository;

import com.erp.core.domain.BranchVariantStockLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface BranchVariantStockLogRepository extends JpaRepository<BranchVariantStockLog, UUID> {

    /**
     * Lịch sử biến động tồn (mới nhất trước). Dùng derived query thay cho @Query
     * "? is null" vì Postgres không đoán được kiểu param UUID/Instant null -&gt; 500.
     */
    Page<BranchVariantStockLog> findByBranchIdAndVariantIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
        UUID branchId, UUID variantId, String status, Instant from, Instant to, Pageable pageable);

    Page<BranchVariantStockLog> findByBranchIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
        UUID branchId, String status, Instant from, Instant to, Pageable pageable);
}
