package com.erp.backend_service.security;

import com.erp.core.dto.auth.ScopeResponse;
import com.erp.core.enums.PrincipalType;
import com.erp.core.enums.ScopeType;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Cung cấp các hàm tạo, trích xuất và kiểm tra tính hợp lệ của JWT
 * (access token / refresh token) dựa trên khóa ký được cấu hình.
 * Mọi token mang claim {@code principal_type} để phân biệt ACCOUNT / CUSTOMER.
 */
@Component
public class JwtProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtProvider.class);

    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";
    public static final String TOKEN_TYPE_EMAIL_VERIFY = "EMAIL_VERIFY";
    public static final String TOKEN_TYPE_PASSWORD_RESET = "PASSWORD_RESET";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_TOKEN_TYPE = "type";
    public static final String CLAIM_ROLE_CODES = "roleCodes";
    public static final String CLAIM_PRINCIPAL_TYPE = "principalType";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_SCOPES = "scopes";
    public static final String CLAIM_BRANCH_ID = "branchId";

    private static final String SCOPE_VALUE_SEPARATOR = "|";

    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;
    private final long emailVerifyExpiry;
    private final long passwordResetExpiry;
    private final SecretKey signingKey;

    public JwtProvider(@Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.access-token-expiry}") long accessTokenExpiry,
                       @Value("${app.jwt.refresh-token-expiry}") long refreshTokenExpiry,
                       @Value("${app.otp.expiry-seconds:900}") long emailVerifyExpiry,
                       @Value("${app.jwt.password-reset-expiry:900}") long passwordResetExpiry) {
        this.accessTokenExpiry = accessTokenExpiry;
        this.refreshTokenExpiry = refreshTokenExpiry;
        this.emailVerifyExpiry = emailVerifyExpiry;
        this.passwordResetExpiry = passwordResetExpiry;
        this.signingKey = createSigningKey(secret);
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

    /** Tạo access token chứa principalId, loại thực thể, username và vai trò. */
    public String generateAccessToken(CustomUserDetails userDetails) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (accessTokenExpiry * 1000L));

        var builder = Jwts.builder()
                .subject(userDetails.getPrincipalId().toString())
                .claim(CLAIM_PRINCIPAL_TYPE, userDetails.getPrincipalType().name())
                .claim(CLAIM_USERNAME, userDetails.getUsername())
                .claim(CLAIM_ROLE_CODES, userDetails.getRoles())
                .claim(CLAIM_SCOPES, serializeScopes(userDetails.getScopes()))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiryDate);
        if (userDetails.getBranchId() != null) {
            builder.claim(CLAIM_BRANCH_ID, userDetails.getBranchId().toString());
        }
        return builder.signWith(signingKey).compact();
    }

    /** Mã hóa danh sách phạm vi (scope) thành chuỗi lưu trong claim token. */
    private List<String> serializeScopes(List<ScopeResponse> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(scopes.size());
        for (ScopeResponse scope : scopes) {
            String branchIdValue = scope.branchId() == null ? "" : scope.branchId().toString();
            result.add(String.join(SCOPE_VALUE_SEPARATOR,
                    scope.id().toString(), scope.scopeType().name(), branchIdValue));
        }
        return result;
    }

    /** Tạo refresh token tối giản (chỉ chứa principalId, loại thực thể và loại token). */
    public String generateRefreshToken(PrincipalType principalType, UUID principalId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (refreshTokenExpiry * 1000L));

        return Jwts.builder()
                .subject(principalId.toString())
                .claim(CLAIM_PRINCIPAL_TYPE, principalType.name())
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

    /** Trích xuất principalId từ subject của token. */
    public UUID extractPrincipalId(String token) {
        Claims claims = extractAllClaims(token);
        return UUID.fromString(claims.getSubject());
    }

    /** Trích xuất loại thực thể (ACCOUNT / CUSTOMER) từ token. */
    public PrincipalType extractPrincipalType(String token) {
        Claims claims = extractAllClaims(token);
        String value = claims.get(CLAIM_PRINCIPAL_TYPE, String.class);
        return value == null ? PrincipalType.ACCOUNT : PrincipalType.valueOf(value);
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

    /** Tạo token xác thực email (dùng cho phiên đăng ký / gửi lại OTP). */
    public String generateEmailVerifyToken(PrincipalType principalType, UUID principalId, String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (emailVerifyExpiry * 1000L));

        return Jwts.builder()
                .subject(principalId.toString())
                .claim(CLAIM_PRINCIPAL_TYPE, principalType.name())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_EMAIL_VERIFY)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }

    /** Kiểm tra token có phải là token xác thực email hay không. */
    public boolean isEmailVerifyToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return TOKEN_TYPE_EMAIL_VERIFY.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
        } catch (Exception e) {
            return false;
        }
    }

    /** Trích xuất email từ token xác thực email. */
    public String extractEmail(String token) {
        return extractAllClaims(token).get(CLAIM_EMAIL, String.class);
    }

    /** Tạo token đặt lại mật khẩu (reset token) cho phiên quên mật khẩu. */
    public String generatePasswordResetToken(PrincipalType principalType, UUID principalId, String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (passwordResetExpiry * 1000L));

        return Jwts.builder()
                .subject(principalId.toString())
                .claim(CLAIM_PRINCIPAL_TYPE, principalType.name())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_PASSWORD_RESET)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }

    /** Kiểm tra token có phải là token đặt lại mật khẩu hay không. */
    public boolean isPasswordResetToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return TOKEN_TYPE_PASSWORD_RESET.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
        } catch (Exception e) {
            return false;
        }
    }
}
