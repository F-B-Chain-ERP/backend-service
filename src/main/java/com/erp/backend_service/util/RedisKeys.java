package com.erp.backend_service.util;

import java.util.UUID;

/**
 * Tạo các khóa Redis theo chuẩn tiền tố, dùng cho token, giới hạn đăng nhập
 * và thu hồi tài khoản.
 */
public final class RedisKeys {
    private static final String REVOCATION_PREFIX = "auth:revoked:account:";
    private static final String PERMISSION_SNAPSHOT_PREFIX = "auth:permission-snapshot:account:";

    private RedisKeys() {
    }

    /** Khóa đánh dấu thời điểm thu hồi tài khoản (vô hiệu access token cũ). */
    public static String accountRevocation(UUID accountId) { return REVOCATION_PREFIX + accountId; }

    /** Khóa lưu quyền/phạm vi của tài khoản trong 15 phút. */
    public static String permissionSnapshot(UUID accountId) { return PERMISSION_SNAPSHOT_PREFIX + accountId; }

}
