package com.erp.backend_service.service;

import com.erp.core.enums.PrincipalType;

import java.util.UUID;

/**
 * Quản lý vòng đời refresh token lưu trên DB (mô hình đa hình theo principal).
 */
public interface RefreshTokenService {

    /**
     * Phát hành và lưu một refresh token mới (chỉ lưu mã băm).
     *
     * @param principalType loại thực thể (ACCOUNT / CUSTOMER)
     * @param principalId   id thực thể sở hữu token
     * @param rawToken      chuỗi token gốc (sẽ được băm trước khi lưu)
     * @param deviceInfo    thông tin thiết bị (tùy chọn)
     * @param ipAddress     địa chỉ IP phát hành (tùy chọn)
     */
    void issue(PrincipalType principalType, UUID principalId, String rawToken, String deviceInfo, String ipAddress);

    /**
     * Xác thực và tiêu thụ (thu hồi) một refresh token theo cơ chế dùng 1 lần.
     * Ném {@code BadRequestException} nếu token không hợp lệ / đã thu hồi / hết hạn.
     *
     * @param principalType loại thực thể
     * @param principalId    id thực thể
     * @param rawToken       chuỗi token gốc
     */
    void consume(PrincipalType principalType, UUID principalId, String rawToken);

    /** Thu hồi một refresh token cụ thể. */
    void revoke(PrincipalType principalType, UUID principalId, String rawToken);

    /** Thu hồi toàn bộ refresh token đang active của một principal (đăng xuất mọi nơi). */
    void revokeAll(PrincipalType principalType, UUID principalId);
}
