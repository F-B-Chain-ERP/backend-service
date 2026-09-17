package com.erp.backend_service.repository;

import com.erp.core.domain.ShiftReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository truy xuất biên bản báo cáo chốt ca và kiểm két.
 */
public interface ShiftReportRepository extends JpaRepository<ShiftReport, UUID>, JpaSpecificationExecutor<ShiftReport> {

    Optional<ShiftReport> findByAssignmentId(UUID assignmentId);

    Optional<ShiftReport> findByIdAndBranchId(UUID id, UUID branchId);

    List<ShiftReport> findByBranchIdAndBusinessDate(UUID branchId, LocalDate businessDate);

    Page<ShiftReport> findByBranchId(UUID branchId, Pageable pageable);

    Page<ShiftReport> findByBranchIdAndBusinessDate(UUID branchId, LocalDate businessDate, Pageable pageable);
}
