package com.erp.backend_service.service.impl;

import com.erp.backend_service.service.AccountRevocationService;
import com.erp.backend_service.util.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Triển khai {@link AccountRevocationService}: lưu mốc thời gian thu hồi tài khoản
 * lên Redis để vô hiệu hóa các access token được cấp trước thời điểm đó.
 */
@Service
public class AccountRevocationServiceImpl implements AccountRevocationService {

    private static final Logger log = LoggerFactory.getLogger(AccountRevocationServiceImpl.class);

    private final StringRedisTemplate stringRedisTemplate;

    public AccountRevocationServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void revokeAccount(UUID accountId, Duration ttl) {
        if (accountId == null) {
            return;
        }
        String key = RedisKeys.accountRevocation(accountId);
        long nowMillis = Instant.now().toEpochMilli();
        try {
            stringRedisTemplate.opsForValue().set(key, String.valueOf(nowMillis), ttl);
            log.info("Account {} revoked in Redis until {}", accountId, Instant.now().plus(ttl));
        } catch (Exception e) {
            log.error("Failed to set revocation timestamp for account {}", accountId, e);
        }
    }

    @Override
    public boolean isRevoked(UUID accountId, Instant tokenIssuedAt) {
        if (accountId == null || tokenIssuedAt == null) {
            return false;
        }
        String key = RedisKeys.accountRevocation(accountId);
        try {
            String revokedAtStr = stringRedisTemplate.opsForValue().get(key);
            if (revokedAtStr == null) {
                return false;
            }
            Instant revokedAt = Instant.ofEpochMilli(Long.parseLong(revokedAtStr));
            return tokenIssuedAt.isBefore(revokedAt);
        } catch (Exception e) {
            log.error("Failed to query revocation status for account {}", accountId, e);
            return false;
        }
    }
}
