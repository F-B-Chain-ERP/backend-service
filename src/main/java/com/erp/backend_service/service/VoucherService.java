package com.erp.backend_service.service;

import com.erp.core.dto.request.menu.ApplyVoucherRequest;
import com.erp.core.dto.request.menu.CreateVoucherRequest;
import com.erp.core.dto.request.menu.UpdateVoucherRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.VoucherApplyResponse;
import com.erp.core.dto.response.menu.VoucherDetailResponse;
import com.erp.core.dto.response.menu.VoucherResponse;
import java.time.Instant;
import java.util.UUID;

/**
 * Nghiệp vụ voucher: truy vấn phân trang, tạo, cập nhật, thay đổi trạng thái,
 * xóa mềm và áp dụng voucher vào đơn hàng.
 */
public interface VoucherService {

    PageResponse<VoucherResponse> list(int page, int size, String search, String status, String discountType,
                                       Instant startFrom, Instant startTo, Instant endFrom, Instant endTo,
                                       UUID branchId);

    VoucherDetailResponse get(UUID id);

    VoucherResponse create(CreateVoucherRequest request);

    VoucherResponse update(UUID id, UpdateVoucherRequest request);

    VoucherResponse updateStatus(UUID id, String status);

    void delete(UUID id);

    VoucherApplyResponse apply(ApplyVoucherRequest request);
}