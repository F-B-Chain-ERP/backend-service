package com.erp.backend_service.service;

import com.erp.core.dto.request.branch.CreateBranchRequest;
import com.erp.core.dto.request.branch.UpdateBranchRequest;
import com.erp.core.dto.response.branch.BranchResponse;

import java.util.List;
import java.util.UUID;

/**
 * Cung cấp nghiệp vụ quản lý chi nhánh: truy vấn, tạo, cập nhật, xóa và
 * lấy danh sách chi nhánh thuộc phạm vi (scope) của tài khoản đang đăng nhập.
 */
public interface BranchService {

    /** Danh sách tất cả chi nhánh (kèm tên chi nhánh cha). */
    List<BranchResponse> findAll();

    /** Danh sách chi nhánh thuộc phạm vi quyền của tài khoản hiện tại. */
    List<BranchResponse> findMine();

    /** Danh sách chi nhánh được gán cho một tài khoản theo accountId. */
    List<BranchResponse> findByAccountId(UUID accountId);

    /** Chi tiết một chi nhánh theo id. */
    BranchResponse findById(UUID id);

    /** Tạo mới chi nhánh. */
    BranchResponse create(CreateBranchRequest request);

    /** Cập nhật chi nhánh. */
    BranchResponse update(UUID id, UpdateBranchRequest request);

    /** Xóa chi nhánh. */
    void delete(UUID id);
}
