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

@Component
public class JwtProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtProvider.class);

    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-token-expiry:900}")
    private long accessTokenExpiry;

    @Value("${app.jwt.refresh-token-expiry:604800}")
    private long refreshTokenExpiry;

    public long getAccessTokenExpiry() {
        return accessTokenExpiry;
    }

    public long getRefreshTokenExpiry() {
        return refreshTokenExpiry;
    }

    private SecretKey signingKey() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (Exception e) {
            keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate standard Access Token containing essential user claims for stateless authorization.
     */
    public String generateAccessToken(CustomUserDetails userDetails) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (accessTokenExpiry * 1000L));

        return Jwts.builder()
                .subject(userDetails.getAccountId().toString())
                .claim("username", userDetails.getUsername())
                .claim("fullName", userDetails.getFullName())
                .claim("email", userDetails.getEmail())
                .claim("roles", userDetails.getRoles())
                .claim("permissions", userDetails.getPermissions())
                .claim("type", TOKEN_TYPE_ACCESS)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey())
                .compact();
    }

    /**
     * Generate minimal Refresh Token.
     */
    public String generateRefreshToken(UUID accountId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (refreshTokenExpiry * 1000L));

        return Jwts.builder()
                .subject(accountId.toString())
                .claim("type", TOKEN_TYPE_REFRESH)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey())
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractAccountId(String token) {
        Claims claims = extractAllClaims(token);
        return UUID.fromString(claims.getSubject());
    }

    public String extractUsername(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("username", String.class);
    }

    public Instant extractIssuedAt(String token) {
        Claims claims = extractAllClaims(token);
        Date iat = claims.getIssuedAt();
        return iat != null ? iat.toInstant() : Instant.now();
    }

    public boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return TOKEN_TYPE_ACCESS.equals(claims.get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isRefreshToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return TOKEN_TYPE_REFRESH.equals(claims.get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }
}
