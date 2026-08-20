package com.erp.backend_service.service;

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
}
