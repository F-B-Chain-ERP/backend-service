package com.erp.backend_service.service;

import com.erp.core.dto.request.proc.CreatePurchaseOrderRequest;
import com.erp.core.dto.request.proc.UpdatePurchaseOrderRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.PurchaseOrderResponse;

import java.util.UUID;

/**
 * Cung cấp nghiệp vụ quản lý đơn mua hàng: truy vấn phân trang, tạo (kèm dòng chi tiết),
 * cập nhật (chỉ DRAFT), xóa (chỉ DRAFT) và các chuyển trạng thái (submit/approve/cancel).
 */
public interface PurchaseOrderService {

    /** Danh sách đơn mua hàng phân trang (lọc theo mã và trạng thái). */
    PageResponse<PurchaseOrderResponse> list(int page, int size, String search, String status);

    /** Chi tiết một đơn mua hàng (kèm dòng chi tiết). */
    PurchaseOrderResponse get(UUID id);

    /** Tạo mới đơn mua hàng ở trạng thái DRAFT, kèm các dòng chi tiết. */
    PurchaseOrderResponse create(CreatePurchaseOrderRequest request);

    /** Cập nhật đơn mua hàng (chỉ khi DRAFT). Dòng chi tiết được thay thế nếu cung cấp. */
    PurchaseOrderResponse update(UUID id, UpdatePurchaseOrderRequest request);

    /** Xóa đơn mua hàng (chỉ khi DRAFT). */
    void delete(UUID id);

    /** Chuyển DRAFT -> SUBMITTED. */
    PurchaseOrderResponse submit(UUID id);

    /** Chuyển SUBMITTED -> APPROVED, ghi nhận người duyệt. */
    PurchaseOrderResponse approve(UUID id);

    /** Hủy đơn mua hàng, ghi nhận lý do. */
    PurchaseOrderResponse cancel(UUID id, String reason);
}
