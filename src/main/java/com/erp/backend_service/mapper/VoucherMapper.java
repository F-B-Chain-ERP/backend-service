package com.erp.backend_service.mapper;

import com.erp.core.domain.Voucher;
import com.erp.core.dto.response.menu.VoucherResponse;
import org.springframework.stereotype.Component;

/** Chuyển đổi entity Voucher sang VoucherResponse. */
@Component
public class VoucherMapper {

    /** Ánh xạ thông tin voucher sang response. */
    public VoucherResponse toResponse(Voucher voucher) {
        return new VoucherResponse(
                voucher.getId().toString(),
                voucher.getCode(),
                voucher.getName(),
                voucher.getDescription(),
                voucher.getDiscountType(),
                voucher.getDiscountValue(),
                voucher.getMaxDiscountAmount(),
                voucher.getMinOrderAmount(),
                voucher.getUsageLimit(),
                voucher.getUsedCount(),
                voucher.getUsageLimitPerCustomer(),
                voucher.getStartAt(),
                voucher.getEndAt(),
                voucher.getStatus(),
                voucher.getCreatedBy(),
                voucher.getCreatedAt(),
                voucher.getUpdatedBy(),
                voucher.getUpdatedAt());
    }
}