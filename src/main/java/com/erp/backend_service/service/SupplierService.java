package com.erp.backend_service.service;

import com.erp.core.dto.request.proc.CreateSupplierRequest;
import com.erp.core.dto.request.proc.UpdateSupplierRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.SupplierResponse;

import java.util.UUID;

/**
 * Cung cấp nghiệp vụ quản lý nhà cung cấp: truy vấn phân trang, tạo, cập nhật, xóa.
 */
public interface SupplierService {

    /** Danh sách nhà cung cấp phân trang (tìm kiếm theo mã/tên, lọc theo trạng thái). */
    PageResponse<SupplierResponse> list(int page, int size, String search, String status);

    /** Chi tiết một nhà cung cấp. */
    SupplierResponse get(UUID id);

    /** Tạo mới nhà cung cấp. */
    SupplierResponse create(CreateSupplierRequest request);

    /** Cập nhật nhà cung cấp. */
    SupplierResponse update(UUID id, UpdateSupplierRequest request);

    /** Xóa nhà cung cấp. */
    void delete(UUID id);
}
