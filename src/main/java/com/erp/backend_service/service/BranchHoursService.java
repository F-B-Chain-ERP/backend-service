package com.erp.backend_service.service;

import com.erp.core.dto.request.branch.BatchUpdateBranchHoursRequest;
import com.erp.core.dto.response.branch.BranchHoursResponse;

import java.util.List;
import java.util.UUID;

/**
 * Nghiệp vụ quản lý lịch hoạt động mở/đóng cửa tuần của chi nhánh.
 */
public interface BranchHoursService {

    /**
     * Lấy danh sách lịch hoạt động 7 ngày trong tuần của chi nhánh.
     * Nếu chi nhánh chưa có cấu hình, tự động khởi tạo mặc định 7 ngày (07:00 - 22:00).
     */
    List<BranchHoursResponse> getHours(UUID branchId);

    /**
     * Cập nhật hàng loạt (Batch Update) lịch hoạt động tuần của chi nhánh.
     */
    List<BranchHoursResponse> updateHours(UUID branchId, BatchUpdateBranchHoursRequest request);

    /**
     * Khởi tạo 7 bản ghi mặc định (Thứ Hai đến Chủ Nhật: 07:00 - 22:00, ACTIVE) cho chi nhánh mới.
     */
    void initDefaultHours(UUID branchId);
}
