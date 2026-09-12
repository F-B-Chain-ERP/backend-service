package com.erp.backend_service.service;

import com.erp.core.dto.request.menu.CreateToppingRequest;
import com.erp.core.dto.request.menu.UpdateToppingRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.ToppingResponse;

import java.util.UUID;

/**
 * Cung cấp nghiệp vụ quản lý topping: truy vấn phân trang, tạo, cập nhật, xóa (hard delete).
 * Topping là nguyên liệu phụ thêm vào đồ uống (thạch, pudding, kem...).
 */
public interface ToppingService {

    /** Danh sách topping phân trang (tìm kiếm theo mã/tên, lọc theo nhóm/trạng thái). */
    PageResponse<ToppingResponse> list(int page, int size, String search, String groupName, String status);

    /** Chi tiết một topping theo ID. */
    ToppingResponse get(UUID id);

    /** Tạo mới topping. Kiểm tra trùng mã và validate mapping material. */
    ToppingResponse create(CreateToppingRequest request);

    /** Cập nhật topping. Kiểm tra trùng mã, validate mapping material và trạng thái. */
    ToppingResponse update(UUID id, UpdateToppingRequest request);

    /** Xóa topping (hard delete). Kiểm tra topping chưa được gán cho sản phẩm nào. */
    void delete(UUID id);
}
