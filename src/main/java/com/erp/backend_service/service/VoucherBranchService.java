package com.erp.backend_service.service;

import com.erp.core.dto.request.menu.AssignVoucherBranchRequest;
import com.erp.core.dto.response.menu.VoucherBranchResponse;
import java.util.List;
import java.util.UUID;

/**
 * Nghiệp vụ gán voucher cho chi nhánh: xem danh sách, gán thêm, gỡ bỏ.
 */
public interface VoucherBranchService {

    List<VoucherBranchResponse> getBranches(UUID voucherId);

    List<VoucherBranchResponse> assign(UUID voucherId, AssignVoucherBranchRequest request);

    void remove(UUID voucherId, UUID branchId);
}