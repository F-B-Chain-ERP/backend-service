package com.erp.backend_service.repository;

import com.erp.core.domain.StoreDailyReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository truy xuất báo cáo ngày của chi nhánh cửa hàng.
 */
public interface StoreDailyReportRepository extends JpaRepository<StoreDailyReport, UUID>, JpaSpecificationExecutor<StoreDailyReport> {

    Optional<StoreDailyReport> findByBranchIdAndBusinessDate(UUID branchId, LocalDate businessDate);

    boolean existsByBranchIdAndBusinessDate(UUID branchId, LocalDate businessDate);

    Optional<StoreDailyReport> findByIdAndBranchId(UUID id, UUID branchId);

    Page<StoreDailyReport> findByBranchId(UUID branchId, Pageable pageable);
}
