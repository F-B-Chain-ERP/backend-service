package com.erp.backend_service.mapper;

import com.erp.core.domain.VoucherBranch;
import com.erp.core.dto.response.menu.VoucherBranchResponse;
import org.springframework.stereotype.Component;

/** Chuyển đổi entity VoucherBranch sang VoucherBranchResponse (kèm tên chi nhánh). */
@Component
public class VoucherBranchMapper {

    /** Ánh xạ bản ghi gán voucher - chi nhánh sang response. */
    public VoucherBranchResponse toResponse(VoucherBranch voucherBranch, String branchName) {
        return new VoucherBranchResponse(
                voucherBranch.getId().toString(),
                voucherBranch.getVoucherId().toString(),
                voucherBranch.getBranchId().toString(),
                branchName,
                voucherBranch.getStatus(),
                voucherBranch.getCreatedBy(),
                voucherBranch.getCreatedAt(),
                voucherBranch.getUpdatedBy(),
                voucherBranch.getUpdatedAt());
    }
}