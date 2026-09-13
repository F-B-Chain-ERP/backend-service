package com.erp.backend_service.repository;

import com.erp.core.domain.VoucherBranch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Truy vấn dữ liệu gán voucher cho chi nhánh (voucher_branch). */
@Repository
public interface VoucherBranchRepository extends JpaRepository<VoucherBranch, UUID> {

    /** Danh sách chi nhánh được gán cho một voucher. */
    List<VoucherBranch> findByVoucherId(UUID voucherId);

    /** Bản ghi gán một chi nhánh của một voucher. */
    Optional<VoucherBranch> findByVoucherIdAndBranchId(UUID voucherId, UUID branchId);

    /** Kiểm tra voucher đã được gán cho chi nhánh hay chưa (không quan tâm trạng thái). */
    boolean existsByVoucherIdAndBranchId(UUID voucherId, UUID branchId);

    /** Kiểm tra voucher đang được gán active cho chi nhánh. */
    boolean existsByVoucherIdAndBranchIdAndStatus(UUID voucherId, UUID branchId, String status);
}