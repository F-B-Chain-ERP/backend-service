package com.erp.backend_service.security;

import com.erp.core.dto.auth.ScopeResponse;

import java.util.List;

/**
 * Ảnh chụp quyền hạn của một tài khoản (vai trò, quyền, phạm vi) được lưu tạm
 * trên cache (Redis) để truy xuất nhanh thay vì tính toán lại từ DB mỗi request.
 *
 * @param roles       danh sách mã vai trò (đã có tiền tố ROLE_)
 * @param permissions danh sách mã quyền
 * @param scopes      danh sách phạm vi áp dụng
 */
public record PermissionSnapshot(
        List<String> roles,
        List<String> permissions,
        List<ScopeResponse> scopes
) {
}
