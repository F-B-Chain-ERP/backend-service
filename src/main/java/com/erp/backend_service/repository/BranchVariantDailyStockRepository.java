package com.erp.backend_service.repository;

import com.erp.core.domain.BranchVariantDailyStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface BranchVariantDailyStockRepository extends JpaRepository<BranchVariantDailyStock, UUID> {
    Optional<BranchVariantDailyStock> findByBranchIdAndVariantIdAndBusinessDateAndStatus(UUID branchId, UUID variantId,
                                                                                         LocalDate date, String status);
}
