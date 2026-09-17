package com.erp.backend_service.repository;

import com.erp.core.domain.Shift;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository truy xuất dữ liệu khung ca làm việc (Shift Template).
 */
public interface ShiftRepository extends JpaRepository<Shift, UUID>, JpaSpecificationExecutor<Shift> {

    Optional<Shift> findByIdAndBranchId(UUID id, UUID branchId);

    List<Shift> findByBranchId(UUID branchId);

    Page<Shift> findByBranchId(UUID branchId, Pageable pageable);

    List<Shift> findByBranchIdAndStatus(UUID branchId, String status);

    boolean existsByBranchIdAndShiftCode(UUID branchId, String shiftCode);

    boolean existsByBranchIdAndShiftCodeAndIdNot(UUID branchId, String shiftCode, UUID id);
}
