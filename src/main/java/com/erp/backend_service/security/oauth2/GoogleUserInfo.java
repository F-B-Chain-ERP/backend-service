package com.erp.backend_service.security.oauth2;

/**
 * Thông tin người dùng Google đã được xác minh từ ID token.
 *
 * @param sub           Google subject id (dùng làm providerId)
 * @param email         email đã xác minh
 * @param emailVerified email có được Google xác thực hay không
 * @param name          họ tên đầy đủ
 * @param picture       url ảnh đại diện
 */
public record GoogleUserInfo(
        String sub,
        String email,
        boolean emailVerified,
        String name,
        String picture
) {
}
