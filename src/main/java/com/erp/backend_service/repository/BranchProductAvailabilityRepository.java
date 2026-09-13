package com.erp.backend_service.repository;

import com.erp.core.domain.BranchProductAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BranchProductAvailabilityRepository extends JpaRepository<BranchProductAvailability, UUID> {
    Optional<BranchProductAvailability> findByBranchIdAndProductIdAndStatus(UUID branchId, UUID productId,
                                                                            String status);
}
