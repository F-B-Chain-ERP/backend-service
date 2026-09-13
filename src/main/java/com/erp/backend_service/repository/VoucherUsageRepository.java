package com.erp.backend_service.repository;

import com.erp.core.domain.VoucherUsage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Truy vấn dữ liệu lịch sử sử dụng voucher (voucher_usage). */
@Repository
public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, UUID> {

    /** Lịch sử sử dụng của một voucher, phân trang theo thời gian sử dụng. */
    Page<VoucherUsage> findByVoucherId(UUID voucherId, Pageable pageable);

    /** Bản ghi sử dụng của một voucher cho một đơn hàng (idempotency). */
    Optional<VoucherUsage> findByVoucherIdAndOrderId(UUID voucherId, UUID orderId);

    /** Số lần một khách hàng đã dùng voucher. */
    long countByVoucherIdAndCustomerId(UUID voucherId, UUID customerId);
}