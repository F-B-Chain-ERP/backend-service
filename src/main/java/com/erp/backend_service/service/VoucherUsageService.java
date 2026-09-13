package com.erp.backend_service.service;

import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.VoucherUsageResponse;
import java.util.UUID;

/**
 * Nghiệp vụ lịch sử sử dụng voucher.
 */
public interface VoucherUsageService {

    PageResponse<VoucherUsageResponse> listByVoucher(int page, int size, UUID voucherId);
}