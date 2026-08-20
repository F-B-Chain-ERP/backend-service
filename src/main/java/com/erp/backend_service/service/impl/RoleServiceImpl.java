package com.erp.backend_service.service.impl;

import com.erp.backend_service.util.audit.AuditAction;
import com.erp.backend_service.util.audit.AuditEvent;
import com.erp.backend_service.util.audit.AuditModule;
import com.erp.backend_service.util.audit.AuditTargetType;
import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.RoleAssignmentMapper;
import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.repository.AccountRoleRepository;
import com.erp.backend_service.repository.RoleRepository;
import com.erp.backend_service.repository.ScopeRepository;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.AccountRevocationService;
import com.erp.backend_service.service.AuditService;
import com.erp.backend_service.service.RoleService;
import com.erp.core.domain.AccountRole;
import com.erp.core.domain.Scope;
import com.erp.core.dto.auth.RoleAssignmentRequest;
import com.erp.core.dto.auth.RoleAssignmentResponse;
import com.erp.core.enums.EntityStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Triển khai {@link RoleService}: gán/thu hồi vai trò cho tài khoản, kiểm tra
 * tính hợp lệ của tài khoản/vai trò/phạm vi và thu hồi token khi có thay đổi.
 */
@Service
public class RoleServiceImpl implements RoleService {
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final ScopeRepository scopeRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final AuditService auditService;
    private final AccountRevocationService revocationService;
    private final RoleAssignmentMapper assignmentMapper;
    private final Duration accessTokenLifetime;

    public RoleServiceImpl(AccountRepository accountRepository,
                           RoleRepository roleRepository,
                            ScopeRepository scopeRepository,
                             AccountRoleRepository accountRoleRepository,
                             AuditService auditService,
                             AccountRevocationService revocationService,
                             RoleAssignmentMapper assignmentMapper,
                            @Value("${app.jwt.access-token-expiry}") long accessTokenExpiry) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.scopeRepository = scopeRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.auditService = auditService;
        this.revocationService = revocationService;
        this.assignmentMapper = assignmentMapper;
        this.accessTokenLifetime = Duration.ofSeconds(accessTokenExpiry);
    }
    /**
     * Gán vai trò cho tài khoản tại một phạm vi cụ thể.
     */
    @Override
    @Transactional
    public RoleAssignmentResponse assign(RoleAssignmentRequest request) {
        validateReferences(request);
        Optional<AccountRole> existing = accountRoleRepository
                .findByAccountIdAndRoleIdAndScopeId(request.accountId(), request.roleId(), request.scopeId());
        if (existing.filter(accountRole -> accountRole.getStatus() == EntityStatus.ACTIVE).isPresent()) {
            throw new BaseException(ErrorCode.ASSIGNMENT_EXISTS);
        }

        AccountRole accountRole = existing.orElseGet(AccountRole::new);
        applyAssignment(accountRole, request);
        try {
            accountRole = accountRoleRepository.saveAndFlush(accountRole);
        } catch (DataIntegrityViolationException exception) {
            throw new BaseException(ErrorCode.ASSIGNMENT_EXISTS);
        }

        auditService.record(assignmentEvent(AuditAction.ASSIGN_ROLE, accountRole));
        revocationService.revokeAccount(request.accountId(), accessTokenLifetime);
        Scope scope = scopeRepository.findById(request.scopeId())
                .orElseThrow(() -> new BaseException(ErrorCode.SCOPE_NOT_FOUND));
        return assignmentMapper.toResponse(accountRole, scope);
    }
    /**
     * Thu hồi vai trò khỏi tài khoản.
     */
    @Override
    @Transactional
    public void revoke(UUID assignmentId) {
        AccountRole accountRole = accountRoleRepository.findById(assignmentId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        verifyCanModify(accountRole.getAccountId());
        accountRole.setStatus(EntityStatus.INACTIVE);
        accountRoleRepository.save(accountRole);
        auditService.record(assignmentEvent(AuditAction.REVOKE_ROLE, accountRole));
        revocationService.revokeAccount(accountRole.getAccountId(), accessTokenLifetime);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleAssignmentResponse> findByAccount(UUID accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new BaseException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        List<AccountRole> accountRoles = accountRoleRepository.findByAccountId(accountId);
        Map<UUID, Scope> scopes = scopeRepository.findAllById(
                accountRoles.stream().map(AccountRole::getScopeId).distinct().toList()
        ).stream().collect(Collectors.toMap(Scope::getId, Function.identity()));
        return accountRoles.stream()
                .map(accountRole -> assignmentMapper.toResponse(accountRole, requiredScope(scopes, accountRole)))
                .toList();
    }

    /**
     * Kiểm tra tài khoản, vai trò và phạm vi trong yêu cầu có tồn tại và đang active.
     */
    private void validateReferences(RoleAssignmentRequest request) {
        var account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new BaseException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (account.getStatus() != EntityStatus.ACTIVE) {
            throw new BaseException(ErrorCode.ACCOUNT_INACTIVE);
        }
        var role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new BaseException(ErrorCode.ROLE_NOT_FOUND));
        if (role.getStatus() != EntityStatus.ACTIVE) {
            throw new BaseException(ErrorCode.ROLE_NOT_FOUND);
        }
        var scope = scopeRepository.findById(request.scopeId())
                .orElseThrow(() -> new BaseException(ErrorCode.SCOPE_NOT_FOUND));
        if (scope.getStatus() != EntityStatus.ACTIVE) {
            throw new BaseException(ErrorCode.SCOPE_NOT_FOUND);
        }
    }

    /**
     * Ngăn việc thay đổi vai trò của tài khoản được bảo vệ (hệ thống/admin).
     */
    private void verifyCanModify(UUID accountId) {
        accountRepository.findById(accountId)
                .filter(account -> account.isSystemProtected())
                .ifPresent(account -> {
                    throw new BaseException(ErrorCode.CANNOT_MODIFY_ADMIN);
                });
    }

    /**
     * Điền thông tin từ yêu cầu vào bản ghi gán vai trò (trạng thái, thời gian, người gán).
     */
    private void applyAssignment(AccountRole accountRole, RoleAssignmentRequest request) {
        accountRole.setAccountId(request.accountId());
        accountRole.setRoleId(request.roleId());
        accountRole.setScopeId(request.scopeId());
        accountRole.setStatus(EntityStatus.ACTIVE);
        accountRole.setAssignedAt(Instant.now());
        accountRole.setAssignedBy(SecurityUtils.getCurrentAccountId()
                .map(UUID::toString).orElse("SYSTEM"));
        accountRole.setExpiresAt(request.expiresAt());
    }

    private AuditEvent assignmentEvent(AuditAction action, AccountRole accountRole) {
        return new AuditEvent(
                SecurityUtils.getCurrentAccountId().orElse(null),
                action,
                AuditModule.SYS,
                AuditTargetType.ACCOUNT,
                accountRole.getAccountId(),
                Map.of(
                        "assignmentId", accountRole.getId().toString(),
                        "roleId", accountRole.getRoleId().toString(),
                        "scopeId", accountRole.getScopeId().toString()
                )
        );
    }

    private Scope requiredScope(Map<UUID, Scope> scopes, AccountRole accountRole) {
        Scope scope = scopes.get(accountRole.getScopeId());
        if (scope == null) {
            throw new IllegalStateException("AccountRole references a missing scope: " + accountRole.getId());
        }
        return scope;
    }
}
