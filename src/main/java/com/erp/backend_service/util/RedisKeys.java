package com.erp.backend_service.util;

import java.util.UUID;

/**
 * Tạo các khóa Redis theo chuẩn tiền tố, dùng cho token, giới hạn đăng nhập
 * và thu hồi tài khoản.
 */
public final class RedisKeys {
    private static final String REVOCATION_PREFIX = "auth:revoked:account:";
    private static final String PERMISSION_SNAPSHOT_PREFIX = "auth:permission-snapshot:account:";
    private static final String OTP_REGISTER_PREFIX = "auth:otp:register:";
    private static final String OTP_ATTEMPTS_PREFIX = "auth:otp:attempts:";
    private static final String OTP_COOLDOWN_PREFIX = "auth:otp:cooldown:";
    private static final String PWD_RESET_OTP_PREFIX = "auth:pwd-reset:otp:";
    private static final String PWD_RESET_ATTEMPTS_PREFIX = "auth:pwd-reset:attempts:";
    private static final String PWD_RESET_COOLDOWN_PREFIX = "auth:pwd-reset:cooldown:";

    private static final String RATE_LIMIT_AUTH_PREFIX = "ratelimit:authenticated:account:";
    private static final String RATE_LIMIT_ANON_PREFIX = "ratelimit:anonymous:ip:";

    private RedisKeys() {
    }

    /** Khóa đánh dấu thời điểm thu hồi tài khoản (vô hiệu access token cũ). */
    public static String accountRevocation(UUID accountId) { return REVOCATION_PREFIX + accountId; }

    /** Khóa lưu quyền/phạm vi của tài khoản trong 15 phút. */
    public static String permissionSnapshot(UUID accountId) { return PERMISSION_SNAPSHOT_PREFIX + accountId; }

    /** Khóa lưu mã OTP đăng ký/xác thực email của khách hàng (có TTL). */
    public static String otpRegister(UUID customerId) { return OTP_REGISTER_PREFIX + customerId; }

    /** Khóa đếm số lần thử sai OTP của khách hàng (có TTL). */
    public static String otpAttempts(UUID customerId) { return OTP_ATTEMPTS_PREFIX + customerId; }

    /** Khóa giới hạn thời gian gửi lại OTP (cooldown). */
    public static String otpCooldown(UUID customerId) { return OTP_COOLDOWN_PREFIX + customerId; }

    /** Khóa lưu mã OTP đặt lại mật khẩu (có TTL). */
    public static String passwordResetOtp(UUID principalId) { return PWD_RESET_OTP_PREFIX + principalId; }

    /** Khóa đếm số lần thử sai OTP đặt lại mật khẩu (có TTL). */
    public static String passwordResetAttempts(UUID principalId) { return PWD_RESET_ATTEMPTS_PREFIX + principalId; }

    /** Khóa giới hạn thời gian gửi lại OTP đặt lại mật khẩu (cooldown). */
    public static String passwordResetCooldown(UUID principalId) { return PWD_RESET_COOLDOWN_PREFIX + principalId; }

    /**
     * Khóa giới hạn tốc độ (rate limit) cho user ĐÃ ĐĂNG NHẬP, phân biệt theo TỪNG PHIÊN.
     * Format: {@code ratelimit:authenticated:account:{accountId}:session:{jti}}
     * - {@code accountId}: id tài khoản.
     * - {@code jti}: JWT ID (duy nhất mỗi lần login) → mỗi phiên có quota riêng,
     *   nhiều người cùng 1 tài khoản không cộng dồn vào nhau.
     * TTL: {@code app.rate-limit.authenticated.refill-seconds} (mặc định 60s).
     * Value: số nguyên đếm số request trong cửa sổ (fixed-window counter).
     */
    public static String rateLimitAuthenticated(UUID accountId, String jti) {
        return RATE_LIMIT_AUTH_PREFIX + accountId + ":session:" + jti;
    }

    /**
     * Khóa giới hạn tốc độ (rate limit) cho user ẨN DANH, phân biệt theo IP.
     * Format: {@code ratelimit:anonymous:ip:{ip}}
     * - {@code ip}: địa chỉ IP thực của client (từ X-Forwarded-For hoặc remoteAddr).
     * TTL: {@code app.rate-limit.anonymous.refill-seconds} (mặc định 60s).
     * Value: số nguyên đếm số request trong cửa sổ (fixed-window counter).
     */
    public static String rateLimitAnonymous(String ip) {
        return RATE_LIMIT_ANON_PREFIX + ip;
    }

}
