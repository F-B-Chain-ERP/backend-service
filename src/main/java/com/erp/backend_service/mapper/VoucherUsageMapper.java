package com.erp.backend_service.mapper;

import com.erp.core.domain.VoucherUsage;
import com.erp.core.dto.response.menu.VoucherUsageResponse;
import org.springframework.stereotype.Component;

/** Chuyển đổi entity VoucherUsage sang VoucherUsageResponse. */
@Component
public class VoucherUsageMapper {

    /** Ánh xạ bản ghi sử dụng voucher sang response. */
    public VoucherUsageResponse toResponse(VoucherUsage voucherUsage) {
        return new VoucherUsageResponse(
                voucherUsage.getId().toString(),
                voucherUsage.getVoucherId().toString(),
                voucherUsage.getOrderId().toString(),
                voucherUsage.getCustomerId() != null ? voucherUsage.getCustomerId().toString() : null,
                voucherUsage.getDiscountAmount(),
                voucherUsage.getUsedAt(),
                voucherUsage.getStatus(),
                voucherUsage.getCreatedBy(),
                voucherUsage.getCreatedAt());
    }
}