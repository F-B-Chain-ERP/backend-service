package com.erp.backend_service.repository;

import com.erp.core.domain.BranchToppingAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BranchToppingAvailabilityRepository extends JpaRepository<BranchToppingAvailability, UUID> {
    Optional<BranchToppingAvailability> findByBranchIdAndToppingIdAndStatus(UUID branchId, UUID toppingId,
                                                                              String status);
}
