package com.erp.backend_service.repository;

import com.erp.core.domain.VoucherUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, UUID> {
    long countByVoucherIdAndCustomerIdAndStatus(UUID voucherId, UUID customerId, String status);

    java.util.Optional<VoucherUsage> findByOrderIdAndStatus(UUID orderId, String status);
}
