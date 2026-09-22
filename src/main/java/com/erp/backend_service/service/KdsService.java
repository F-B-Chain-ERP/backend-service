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

    PageResponse<KdsTicketSummaryResponse> list(UUID branchId, String status, LocalDate fromDate,
                                                 LocalDate toDate, String search, int page, int size);

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

    /**
     * Kéo ticket theo Order (Order là nguồn sự thật cho chiều xuôi).
     * KDS chỉ làm tới READY, Order bấm nốt DELIVERING/COMPLETED nên:
     * PREPARING -&gt; ticket QUEUED-&gt;PREPARING, READY -&gt; ticket -&gt;READY,
     * DELIVERING/COMPLETED -&gt; ticket -&gt;SERVED (dọn board).
     */
    void syncFromOrder(UUID orderId, String orderStatus);

    /** Đánh dấu SERVED khi Order hoàn tất / đi giao (dọn board, không đổi ngược Order). */
    void markServedByOrderId(UUID orderId);
}
