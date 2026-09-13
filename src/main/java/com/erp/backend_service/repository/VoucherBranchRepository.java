package com.erp.backend_service.repository;

import com.erp.core.domain.VoucherBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VoucherBranchRepository extends JpaRepository<VoucherBranch, UUID> {
    Optional<VoucherBranch> findByVoucherIdAndBranchIdAndStatus(UUID voucherId, UUID branchId, String status);
}
