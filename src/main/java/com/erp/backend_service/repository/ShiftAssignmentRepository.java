package com.erp.backend_service.repository;

import com.erp.core.domain.ShiftAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository truy xuất lịch phân ca làm việc của nhân viên.
 */
public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, UUID>, JpaSpecificationExecutor<ShiftAssignment> {

    Optional<ShiftAssignment> findByIdAndBranchId(UUID id, UUID branchId);

    boolean existsByBranchId(UUID branchId);

    boolean existsByShiftIdAndAccountIdAndWorkDate(UUID shiftId, UUID accountId, LocalDate workDate);

    boolean existsByBranchIdAndAccountIdAndWorkDate(UUID branchId, UUID accountId, LocalDate workDate);

    boolean existsByBranchIdAndStatus(UUID branchId, String status);

    List<ShiftAssignment> findByBranchIdAndStatus(UUID branchId, String status);

    List<ShiftAssignment> findByBranchIdAndWorkDateAndStatusIn(UUID branchId, LocalDate workDate, Collection<String> statuses);

    boolean existsByShiftIdAndStatusIn(UUID shiftId, Collection<String> statuses);

    Optional<ShiftAssignment> findFirstByAccountIdAndStatus(UUID accountId, String status);

    Optional<ShiftAssignment> findFirstByAccountIdAndWorkDateAndStatus(UUID accountId, LocalDate workDate, String status);

    List<ShiftAssignment> findByBranchIdAndWorkDate(UUID branchId, LocalDate workDate);

    List<ShiftAssignment> findByBranchIdAndWorkDateAndStatus(UUID branchId, LocalDate workDate, String status);

    long countByBranchIdAndWorkDateAndStatusIn(UUID branchId, LocalDate workDate, Collection<String> statuses);
}
