package com.erp.backend_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Cung cấp các hàm tạo, trích xuất và kiểm tra tính hợp lệ của JWT
 * (access token / refresh token) dựa trên khóa ký được cấu hình.
 */
@Component
public class JwtProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtProvider.class);

    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_FULL_NAME = "fullName";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "permissions";
    private static final String CLAIM_SCOPES = "scopes";
    private static final String CLAIM_TOKEN_TYPE = "type";

    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;
    private final SecretKey signingKey;

    public JwtProvider(@Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.access-token-expiry}") long accessTokenExpiry,
                       @Value("${app.jwt.refresh-token-expiry}") long refreshTokenExpiry) {
        this.accessTokenExpiry = accessTokenExpiry;
        this.refreshTokenExpiry = refreshTokenExpiry;
        this.signingKey = createSigningKey(secret);
    }

    /** Lấy thời gian hết hạn của access token (giây). */
    public long getAccessTokenExpiry() {
        return accessTokenExpiry;
    }

    /** Lấy thời gian hết hạn của refresh token (giây). */
    public long getRefreshTokenExpiry() {
        return refreshTokenExpiry;
    }

    /** Tạo khóa ký từ chuỗi bí mật (giải mã base64 nếu có thể). */
    private SecretKey createSigningKey(String secret) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (Exception e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** Tạo access token chứa đầy đủ thông tin tài khoản, vai trò, quyền và phạm vi. */
    public String generateAccessToken(CustomUserDetails userDetails) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (accessTokenExpiry * 1000L));

        return Jwts.builder()
                .subject(userDetails.getAccountId().toString())
                .claim(CLAIM_USERNAME, userDetails.getUsername())
                .claim(CLAIM_FULL_NAME, userDetails.getFullName())
                .claim(CLAIM_EMAIL, userDetails.getEmail())
                .claim(CLAIM_ROLES, userDetails.getRoles())
                .claim(CLAIM_PERMISSIONS, userDetails.getPermissions())
                .claim(CLAIM_SCOPES, userDetails.getScopes())
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }

    /** Tạo refresh token tối giản (chỉ chứa accountId và loại token). */
    public String generateRefreshToken(UUID accountId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (refreshTokenExpiry * 1000L));

        return Jwts.builder()
                .subject(accountId.toString())
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }

    /** Giải mã và trả về toàn bộ claims của token (ném lỗi nếu không hợp lệ). */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Trích xuất accountId từ subject của token. */
    public UUID extractAccountId(String token) {
        Claims claims = extractAllClaims(token);
        return UUID.fromString(claims.getSubject());
    }

    /** Trích xuất tên đăng nhập từ token. */
    public String extractUsername(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get(CLAIM_USERNAME, String.class);
    }

    /** Trích xuất thời điểm phát hành token (ném lỗi nếu thiếu). */
    public Instant extractIssuedAt(String token) {
        Claims claims = extractAllClaims(token);
        Date iat = claims.getIssuedAt();
        if (iat == null) {
            throw new JwtException("Missing issued-at claim");
        }
        return iat.toInstant();
    }

    /** Kiểm tra token có hợp lệ (chữ ký đúng, chưa bị sửa đổi). */
    public boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /** Kiểm tra token có phải là access token hay không. */
    public boolean isAccessToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
        } catch (Exception e) {
            return false;
        }
    }

    /** Kiểm tra token có phải là refresh token hay không. */
    public boolean isRefreshToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return TOKEN_TYPE_REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
        } catch (Exception e) {
            return false;
        }
    }
}
