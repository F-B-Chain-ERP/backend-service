package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BadRequestException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.RefreshTokenRepository;
import com.erp.backend_service.service.RefreshTokenService;
import com.erp.core.domain.RefreshToken;
import com.erp.core.enums.EntityStatus;
import com.erp.core.enums.PrincipalType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Triển khai {@link RefreshTokenService}: lưu mã băm refresh token trên DB,
 * xác thực theo cơ chế dùng 1 lần (single-use) và hỗ trợ thu hồi.
 */
@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenExpiry;

    public RefreshTokenServiceImpl(RefreshTokenRepository refreshTokenRepository,
                                   @Value("${app.jwt.refresh-token-expiry}") long refreshTokenExpiry) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpiry = refreshTokenExpiry;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void issue(PrincipalType principalType, UUID principalId, String rawToken,
                      String deviceInfo, String ipAddress) {
        RefreshToken token = new RefreshToken();
        token.setPrincipalType(principalType);
        token.setPrincipalId(principalId);
        token.setTokenHash(hash(rawToken));
        token.setDeviceInfo(deviceInfo);
        token.setIpAddress(ipAddress);
        token.setExpiresAt(Instant.now().plusSeconds(refreshTokenExpiry));
        token.setStatus(EntityStatus.ACTIVE);
        refreshTokenRepository.save(token);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void consume(PrincipalType principalType, UUID principalId, String rawToken) {
        RefreshToken token = refreshTokenRepository
                .findByTokenHashAndStatus(hash(rawToken), EntityStatus.ACTIVE)
                .orElseThrow(() -> new BadRequestException(ErrorCode.INVALID_TOKEN));
        if (!principalType.equals(token.getPrincipalType()) || !principalId.equals(token.getPrincipalId())) {
            throw new BadRequestException(ErrorCode.INVALID_TOKEN);
        }
        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException(ErrorCode.INVALID_TOKEN);
        }
        token.setStatus(EntityStatus.INACTIVE);
        token.setRevokedAt(Instant.now());
        refreshTokenRepository.save(token);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void revoke(PrincipalType principalType, UUID principalId, String rawToken) {
        refreshTokenRepository.findByTokenHashAndStatus(hash(rawToken), EntityStatus.ACTIVE)
                .ifPresent(token -> {
                    token.setStatus(EntityStatus.INACTIVE);
                    token.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(token);
                });
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void revokeAll(PrincipalType principalType, UUID principalId) {
        refreshTokenRepository.revokeAll(principalType, principalId, EntityStatus.ACTIVE, EntityStatus.INACTIVE);
    }

    /** Băm SHA-256 chuỗi token gốc, trả về dạng hex. */
    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
