package com.erp.backend_service.repository;

import com.erp.core.domain.BranchHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchHoursRepository extends JpaRepository<BranchHours, UUID> {
    Optional<BranchHours> findByBranchIdAndDayOfWeekAndStatus(UUID branchId, Integer dayOfWeek, String status);
    Optional<BranchHours> findByBranchIdAndDayOfWeek(UUID branchId, Integer dayOfWeek);
    List<BranchHours> findByBranchIdOrderByDayOfWeekAsc(UUID branchId);
    List<BranchHours> findByBranchIdAndStatusOrderByDayOfWeekAsc(UUID branchId, String status);
    void deleteByBranchId(UUID branchId);
}
