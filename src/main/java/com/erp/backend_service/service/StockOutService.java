package com.erp.backend_service.service;

import com.erp.core.dto.request.inv.CreateStockOutRequest;
import com.erp.core.dto.request.inv.StatusUpdateRequest;
import com.erp.core.dto.request.inv.UpdateStockOutRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.inv.StockOutResponse;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Nghiệp vụ quản lý phiếu xuất kho (INV): truy vấn phân trang, tạo (kèm dòng chi tiết),
 * cập nhật (chỉ DRAFT), và ghi sổ/hủy (chỉ DRAFT) — POSTED sẽ giảm tồn kho, không cho phép
 * tồn âm.
 */
public interface StockOutService {

    /** Danh sách phiếu xuất kho phân trang (lọc theo mã, trạng thái, kho, loại đích xuất, khoảng ngày). */
    PageResponse<StockOutResponse> list(int page, int size, String search, String status,
                                        UUID warehouseId, String destinationType, LocalDate fromDate, LocalDate toDate);

    /** Chi tiết một phiếu xuất kho (kèm dòng chi tiết). */
    StockOutResponse get(UUID id);

    /** Tạo mới phiếu xuất kho ở trạng thái DRAFT, kèm các dòng chi tiết. */
    StockOutResponse create(CreateStockOutRequest request);

    /** Cập nhật phiếu xuất kho (chỉ khi DRAFT). Dòng chi tiết được thay thế nếu cung cấp. */
    StockOutResponse update(UUID id, UpdateStockOutRequest request);

    /** Ghi sổ (POSTED) hoặc hủy (CANCELLED) phiếu xuất kho — chỉ khi DRAFT. */
    StockOutResponse changeStatus(UUID id, StatusUpdateRequest request);
}