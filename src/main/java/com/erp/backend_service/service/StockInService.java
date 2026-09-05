package com.erp.backend_service.service;

import com.erp.core.dto.request.inv.CreateStockInRequest;
import com.erp.core.dto.request.inv.StatusUpdateRequest;
import com.erp.core.dto.request.inv.UpdateStockInRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.inv.StockInResponse;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Nghiệp vụ quản lý phiếu nhập kho (INV): truy vấn phân trang, tạo (kèm dòng chi tiết),
 * cập nhật (chỉ DRAFT), và ghi sổ/hủy (chỉ DRAFT) — POSTED sẽ tăng tồn kho và cập nhật
 * số lượng nhận của đơn mua hàng nếu nguồn nhập là PURCHASE.
 */
public interface StockInService {

    /** Danh sách phiếu nhập kho phân trang (lọc theo mã, trạng thái, kho, nguồn nhập, khoảng ngày). */
    PageResponse<StockInResponse> list(int page, int size, String search, String status,
                                       UUID warehouseId, String sourceType, LocalDate fromDate, LocalDate toDate);

    /** Chi tiết một phiếu nhập kho (kèm dòng chi tiết). */
    StockInResponse get(UUID id);

    /** Tạo mới phiếu nhập kho ở trạng thái DRAFT, kèm các dòng chi tiết. */
    StockInResponse create(CreateStockInRequest request);

    /** Cập nhật phiếu nhập kho (chỉ khi DRAFT). Dòng chi tiết được thay thế nếu cung cấp. */
    StockInResponse update(UUID id, UpdateStockInRequest request);

    /** Ghi sổ (POSTED) hoặc hủy (CANCELLED) phiếu nhập kho — chỉ khi DRAFT. */
    StockInResponse changeStatus(UUID id, StatusUpdateRequest request);
}