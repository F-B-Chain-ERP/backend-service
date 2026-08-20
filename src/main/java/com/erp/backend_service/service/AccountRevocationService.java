package com.erp.backend_service.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Quản lý việc thu hồi phiên/token của tài khoản: vô hiệu hóa các access token
 * được cấp trước một thời điểm và kiểm tra một token có bị thu hồi hay không.
 */
public interface AccountRevocationService {

    /**
     * Revokes all access tokens for the given account issued before the current time.
     *
     * @param accountId account identifier
     * @param ttl       duration for which the revocation record must be preserved (e.g. max AT validity)
     */
    void revokeAccount(UUID accountId, Duration ttl);

    /**
     * Checks whether the token issued at {@code tokenIssuedAt} for {@code accountId} is revoked.
     *
     * @param accountId     account identifier
     * @param tokenIssuedAt instant the token was issued
     * @return true if revoked, false otherwise
     */
    boolean isRevoked(UUID accountId, Instant tokenIssuedAt);
}
