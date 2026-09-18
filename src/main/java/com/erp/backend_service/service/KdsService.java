package com.erp.backend_service.service;

import com.erp.core.dto.request.pos.KdsTicketItemProgressRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.pos.KdsTicketResponse;
import com.erp.core.dto.response.pos.KdsTicketSummaryResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface KdsService {

    PageResponse<KdsTicketSummaryResponse> list(UUID branchId, String status, LocalDate fromDate,
                                                LocalDate toDate, int page, int size);

    KdsTicketResponse get(UUID id);

    KdsTicketResponse getByOrderId(UUID orderId);

    KdsTicketResponse start(UUID id);

    KdsTicketResponse ready(UUID id);

    KdsTicketResponse serve(UUID id);

    KdsTicketResponse progressItem(UUID itemId, KdsTicketItemProgressRequest request);

    /**
     * Tự tạo 1 ticket BAR cho đơn vừa CONFIRMED. Idempotent theo orderId.
     * Không sửa DB: station cố định BAR, queue_no tăng theo ngày theo chi nhánh.
     */
    KdsTicketResponse createOnOrderConfirmed(UUID orderId);

    /** Hủy ticket khi Order bị CANCELLED/REJECTED. */
    void cancelByOrderId(UUID orderId, String reason);
}
