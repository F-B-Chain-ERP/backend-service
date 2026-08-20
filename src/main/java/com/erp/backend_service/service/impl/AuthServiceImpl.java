package com.erp.backend_service.service.impl;

import com.erp.backend_service.util.audit.AuditAction;
import com.erp.backend_service.util.audit.AuditEvent;
import com.erp.backend_service.util.audit.AuditModule;
import com.erp.backend_service.util.audit.AuditTargetType;
import com.erp.backend_service.exception.BadRequestException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.AuthMapper;
import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.security.CustomUserDetails;
import com.erp.backend_service.security.CustomUserDetailsService;
import com.erp.backend_service.security.JwtProvider;
import com.erp.backend_service.service.AuditService;
import com.erp.backend_service.service.AuthService;
import com.erp.backend_service.service.PermissionService;
import com.erp.core.domain.Account;
import com.erp.core.dto.auth.AuthResponse;
import com.erp.core.dto.auth.LoginRequest;
import com.erp.core.dto.auth.RefreshTokenRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Triển khai {@link AuthService}: xác thực đăng nhập, cấp phát và làm mới JWT,
 * đồng thời ghi nhận sự kiện đăng nhập thành công/thất bại vào audit log.
 */
@Service
public class AuthServiceImpl implements AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;
    private final AccountRepository accountRepository;
    private final AuditService auditService;
    private final AuthMapper authMapper;
    private final PermissionService permissionService;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           JwtProvider jwtProvider,
                           CustomUserDetailsService userDetailsService,
                           AccountRepository accountRepository,
                              AuditService auditService,
                              AuthMapper authMapper,
                              PermissionService permissionService) {
        this.authenticationManager = authenticationManager;
        this.jwtProvider = jwtProvider;
        this.userDetailsService = userDetailsService;
        this.accountRepository = accountRepository;
        this.auditService = auditService;
        this.authMapper = authMapper;
        this.permissionService = permissionService;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AuthResponse login(LoginRequest request) {
        CustomUserDetails userDetails;
        try {
            userDetails = authenticate(request);
        } catch (BadCredentialsException exception) {
            UUID accountId = accountRepository.findByUsernameOrEmail(request.usernameOrEmail(), request.usernameOrEmail())
                    .map(Account::getId).orElse(null);
            auditLogin(accountId, AuditAction.LOGIN_FAILED, Map.of("reason", ErrorCode.BAD_CREDENTIALS.getCode()));
            throw exception;
        }

        Account account = requiredAccount(userDetails.getAccountId());
        account.setLastLoginAt(Instant.now());
        accountRepository.save(account);

        String accessToken = jwtProvider.generateAccessToken(userDetails);
        String refreshToken = jwtProvider.generateRefreshToken(account.getId());
        permissionService.saveSnapshot(account.getId(), permissionService.snapshotFromDetails(userDetails));
        auditLogin(account.getId(), AuditAction.LOGIN_SUCCESS,
                Map.of("requiresScopeAssignment", userDetails.getScopes().isEmpty()));
        return authMapper.toResponse(accessToken, refreshToken, userDetails.getScopes().isEmpty(), account);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String currentToken = request.refreshToken();
        if (!jwtProvider.validateToken(currentToken) || !jwtProvider.isRefreshToken(currentToken)) {
            throw new BadRequestException(ErrorCode.INVALID_TOKEN);
        }

        UUID accountId = jwtProvider.extractAccountId(currentToken);
        CustomUserDetails userDetails = (CustomUserDetails) userDetailsService
                .loadUserByUsername(accountId.toString());
        if (!userDetails.isEnabled()) {
            throw new BadRequestException(ErrorCode.ACCOUNT_DISABLED);
        }

        String newAccessToken = jwtProvider.generateAccessToken(userDetails);
        String newRefreshToken = jwtProvider.generateRefreshToken(accountId);
        permissionService.saveSnapshot(accountId, permissionService.snapshotFromDetails(userDetails));
        return authMapper.toResponse(newAccessToken, newRefreshToken,
                userDetails.getScopes().isEmpty(), requiredAccount(accountId));
    }

    /** {@inheritDoc} */
    @Override
    public void logout(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        String token = accessToken.startsWith("Bearer ")
                ? accessToken.substring("Bearer ".length()).trim()
                : accessToken.trim();
        if (!jwtProvider.validateToken(token) || !jwtProvider.isAccessToken(token)) {
            throw new BadRequestException(ErrorCode.INVALID_TOKEN);
        }
    }

    /**
     * Thực hiện xác thực username/password qua {@code AuthenticationManager}.
     */
    private CustomUserDetails authenticate(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.usernameOrEmail().trim(), request.password())
        );
        return (CustomUserDetails) authentication.getPrincipal();
    }

    /** Ghi nhận một sự kiện đăng nhập (thành công/thất bại) vào audit log. */
    private void auditLogin(UUID accountId, AuditAction action, Map<String, Object> details) {
        auditService.record(new AuditEvent(
                accountId,
                action,
                AuditModule.SYS,
                AuditTargetType.ACCOUNT,
                accountId,
                details
        ));
    }

    /**
     * Lấy tài khoản theo id, ném lỗi nếu không tồn tại.
     */
    private Account requiredAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BadRequestException(ErrorCode.USER_NOT_EXISTED));
    }

}
