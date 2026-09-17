package com.erp.backend_service.repository;

import com.erp.core.domain.BranchHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BranchHoursRepository extends JpaRepository<BranchHours, UUID> {
    Optional<BranchHours> findByBranchIdAndDayOfWeekAndStatus(UUID branchId, Integer dayOfWeek, String status);
}
