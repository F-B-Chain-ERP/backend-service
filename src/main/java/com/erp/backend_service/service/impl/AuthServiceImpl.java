package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BadRequestException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.security.CustomUserDetails;
import com.erp.backend_service.security.CustomUserDetailsService;
import com.erp.backend_service.security.JwtProvider;
import com.erp.backend_service.service.AuthService;
import com.erp.core.dto.auth.AuthResponse;
import com.erp.core.dto.auth.LoginRequest;
import com.erp.core.dto.auth.RefreshTokenRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private static final String REFRESH_TOKEN_KEY_PREFIX = "rt:";

    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final AccountRepository accountRepository;
    private final StringRedisTemplate stringRedisTemplate;

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            JwtProvider jwtProvider,
            CustomUserDetailsService customUserDetailsService,
            AccountRepository accountRepository,
            StringRedisTemplate stringRedisTemplate
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtProvider = jwtProvider;
        this.customUserDetailsService = customUserDetailsService;
        this.accountRepository = accountRepository;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Processing login for user: {}", request.usernameOrEmail());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.usernameOrEmail(), request.password())
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        String accessToken = jwtProvider.generateAccessToken(Objects.requireNonNull(userDetails));
        String refreshToken = jwtProvider.generateRefreshToken(userDetails.getAccountId());

        // Store refresh token in Redis with TTL
        String rtKey = REFRESH_TOKEN_KEY_PREFIX + userDetails.getAccountId();
        try {
            stringRedisTemplate.opsForValue().set(
                    rtKey,
                    refreshToken,
                    Duration.ofSeconds(jwtProvider.getRefreshTokenExpiry())
            );
        } catch (Exception e) {
            log.error("Failed to store refresh token in Redis for user {}", userDetails.getAccountId(), e);
        }

        // Update last login timestamp asynchronously / in current transaction
        accountRepository.findById(userDetails.getAccountId()).ifPresent(account -> {
            account.setLastLoginAt(Instant.now());
            accountRepository.save(account);
        });

        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtProvider.getAccessTokenExpiry()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();

        // 1. Validate signature, expiration and type
        if (!jwtProvider.validateToken(refreshToken) || !jwtProvider.isRefreshToken(refreshToken)) {
            throw new BadRequestException(ErrorCode.INVALID_TOKEN);
        }

        UUID accountId = jwtProvider.extractAccountId(refreshToken);

        // 2. Verify against Redis to prevent token replay attacks
        String rtKey = REFRESH_TOKEN_KEY_PREFIX + accountId;
        String storedRt = stringRedisTemplate.opsForValue().get(rtKey);
        if (storedRt == null || !storedRt.equals(refreshToken)) {
            log.warn("Refresh token reuse or revoked attempt detected for account {}", accountId);
            throw new BadRequestException(ErrorCode.TOKEN_REVOKED);
        }

        // 3. Load up-to-date user details and permissions
        UserDetails ud = customUserDetailsService.loadUserByUsername(accountId.toString());
        if (!(ud instanceof CustomUserDetails userDetails)) {
            throw new BadRequestException(ErrorCode.USER_NOT_EXISTED);
        }

        if (!userDetails.isEnabled()) {
            throw new BadRequestException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 4. Issue new token pair
        String newAccessToken = jwtProvider.generateAccessToken(userDetails);
        String newRefreshToken = jwtProvider.generateRefreshToken(accountId);

        // 5. Rotate refresh token in Redis
        try {
            stringRedisTemplate.opsForValue().set(
                    rtKey,
                    newRefreshToken,
                    Duration.ofSeconds(jwtProvider.getRefreshTokenExpiry())
            );
        } catch (Exception e) {
            log.error("Failed to update refresh token in Redis for user {}", accountId, e);
        }

        return new AuthResponse(
                newAccessToken,
                newRefreshToken,
                "Bearer",
                jwtProvider.getAccessTokenExpiry()
        );
    }

    @Override
    public void logout(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }

        String rawToken = accessToken.startsWith("Bearer ") ? accessToken.substring(7).trim() : accessToken.trim();

        if (jwtProvider.validateToken(rawToken)) {
            try {
                UUID accountId = jwtProvider.extractAccountId(rawToken);
                String rtKey = REFRESH_TOKEN_KEY_PREFIX + accountId;
                stringRedisTemplate.delete(rtKey);
                log.info("User {} logged out, refresh token revoked from Redis", accountId);
            } catch (Exception e) {
                log.warn("Failed to delete refresh token on logout: {}", e.getMessage());
            }
        }
    }
}
