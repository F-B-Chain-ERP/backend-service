package com.erp.backend_service.service;

import com.erp.backend_service.security.CustomUserDetails;
import com.erp.backend_service.security.PermissionSnapshot;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.PermissionResponse;
import com.erp.core.enums.EntityStatus;

import java.util.List;
import java.util.UUID;

/**
 * Cung cấp các tiện ích kiểm tra và bắt buộc quyền truy cập của tài khoản.
 */
public interface PermissionService {

    /**
     * Kiểm tra tài khoản có được cấp quyền với mã quyền đã cho hay không.
     *
     * @param accountId      id của tài khoản cần kiểm tra
     * @param permissionCode mã quyền (ví dụ: USER_VIEW)
     * @return {@code true} nếu tài khoản đang hoạt động và sở hữu quyền này
     */
    boolean hasPermission(UUID accountId, String permissionCode);

    /**
     * Kiểm tra tài khoản có quyền thực hiện hành động trong phạm vi chi nhánh cụ thể hay không.
     *
     * @param accountId      id của tài khoản
     * @param permissionCode mã quyền cần kiểm tra
     * @param branchId       id chi nhánh áp dụng (không được {@code null})
     * @return {@code true} nếu có quyền và quyền đó hợp lệ tại chi nhánh này
     *         (hoặc áp dụng toàn hệ thống)
     */
    boolean isAllowed(UUID accountId, String permissionCode, UUID branchId);

    /**
     * Bắt buộc tài khoản hiện tại phải có quyền, nếu không sẽ ném {@code PERMISSION_DENIED}.
     *
     * @param permissionCode mã quyền bắt buộc
     */
    void requirePermission(String permissionCode);

    /**
     * Bắt buộc tài khoản hiện tại có quyền truy cập vào chi nhánh chỉ định,
     * nếu không sẽ ném {@code CROSS_SCOPE_DENIED}.
     *
     * @param permissionCode mã quyền bắt buộc
     * @param branchId       id chi nhánh áp dụng
     */
    void requireAccess(String permissionCode, UUID branchId);

    /**
     * Lấy ảnh chụp quyền hạn của tài khoản, ưu tiên từ cache, nếu chưa có thì
     * tính toán từ DB và lưu cache.
     *
     * @param accountId id tài khoản
     * @return ảnh chụp quyền hạn (không {@code null})
     */
    PermissionSnapshot getSnapshot(UUID accountId);

    /**
     * Lưu ảnh chụp quyền hạn của tài khoản vào cache.
     *
     * @param accountId id tài khoản
     * @param snapshot  ảnh chụp quyền hạn cần lưu
     */
    void saveSnapshot(UUID accountId, PermissionSnapshot snapshot);

    /**
     * Xoá ảnh chụp quyền hạn đã lưu trên cache của tài khoản.
     *
     * @param accountId id tài khoản
     */
    void evictSnapshot(UUID accountId);

    /**
     * Xây dựng ảnh chụp quyền hạn trực tiếp từ {@link CustomUserDetails} (không qua DB).
     *
     * @param details thông tin người dùng đã xác thực
     * @return ảnh chụp quyền hạn tương ứng
     */
    PermissionSnapshot snapshotFromDetails(CustomUserDetails details);

    /**
     * Lấy quyền theo id.
     *
     * @param id id của quyền
     * @return thông tin quyền
     */
    PermissionResponse getById(UUID id);

    /**
     * Phân trang + tìm kiếm danh sách quyền với bộ lọc tuỳ chọn.
     *
     * @param page   chỉ số trang (bắt đầu từ 0)
     * @param size   số bản ghi mỗi trang (1..100)
     * @param search từ khoá tìm kiếm trên code/name/module (có thể null)
     * @param module lọc theo module chính xác (có thể null)
     * @param status lọc theo trạng thái (có thể null)
     * @return trang dữ liệu quyền
     */
    PageResponse<PermissionResponse> getAll(int page, int size, String search, String module, EntityStatus status);

    /**
     * Danh sách các module đang tồn tại trong danh mục quyền.
     *
     * @return tên module (đã sắp xếp)
     */
    List<String> getModules();
}
