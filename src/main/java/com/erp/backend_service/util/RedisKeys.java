package com.erp.backend_service.util;

import java.util.Locale;
import java.util.UUID;

/**
 * Tạo các khóa Redis theo chuẩn tiền tố, dùng cho token, giới hạn đăng nhập
 * và thu hồi tài khoản.
 */
public final class RedisKeys {
    private static final String REFRESH_TOKEN_PREFIX = "auth:refresh:";
    private static final String LOGIN_FAILURE_PREFIX = "auth:login:failure:";
    private static final String LOGIN_LOCK_PREFIX = "auth:login:lock:";
    private static final String REVOCATION_PREFIX = "auth:revoked:account:";

    private RedisKeys() {
    }

    /** Khóa lưu refresh token của tài khoản. */
    public static String refreshToken(UUID accountId) { return REFRESH_TOKEN_PREFIX + accountId; }

    /** Khóa đánh dấu thời điểm thu hồi tài khoản (vô hiệu access token cũ). */
    public static String accountRevocation(UUID accountId) { return REVOCATION_PREFIX + accountId; }

    /** Khóa đếm số lần đăng nhập thất bại theo định danh. */
    public static String loginFailure(String identifier) { return LOGIN_FAILURE_PREFIX + normalize(identifier); }

    /** Khóa đánh dấu tài khoản đang bị khóa tạm thời do đăng nhập sai nhiều lần. */
    public static String loginLock(String identifier) { return LOGIN_LOCK_PREFIX + normalize(identifier); }

    /** Chuẩn hóa định danh: xóa khoảng trắng và chuyển về chữ thường. */
    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
