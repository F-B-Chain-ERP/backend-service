package com.erp.backend_service.repository;

import com.erp.core.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
    List<Refund> findByOrderId(UUID orderId);

    boolean existsByOrderIdAndStatus(UUID orderId, String status);

    boolean existsByRefundCode(String refundCode);
}
