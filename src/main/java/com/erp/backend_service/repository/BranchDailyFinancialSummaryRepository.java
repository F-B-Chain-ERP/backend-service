package com.erp.backend_service.repository;

import com.erp.core.domain.BranchDailyFinancialSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository truy xuất tổng hợp tài chính ngày của chi nhánh (BranchDailyFinancialSummary).
 */
@Repository
public interface BranchDailyFinancialSummaryRepository
        extends JpaRepository<BranchDailyFinancialSummary, UUID>, JpaSpecificationExecutor<BranchDailyFinancialSummary> {

    Optional<BranchDailyFinancialSummary> findByBranchIdAndBusinessDate(UUID branchId, LocalDate businessDate);
}
