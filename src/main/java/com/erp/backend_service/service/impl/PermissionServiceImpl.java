package com.erp.backend_service.service.impl;

import com.erp.backend_service.util.audit.AuditAction;
import com.erp.backend_service.util.audit.AuditEvent;
import com.erp.backend_service.util.audit.AuditModule;
import com.erp.backend_service.util.audit.AuditTargetType;
import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.repository.AccountRoleRepository;
import com.erp.backend_service.repository.PermissionRepository;
import com.erp.backend_service.repository.RolePermissionRepository;
import com.erp.backend_service.repository.ScopeRepository;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.AuditService;
import com.erp.backend_service.service.PermissionService;
import com.erp.core.domain.AccountRole;
import com.erp.core.domain.Permission;
import com.erp.core.domain.RolePermission;
import com.erp.core.domain.Scope;
import com.erp.core.enums.EntityStatus;
import com.erp.core.enums.ScopeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Triển khai {@link PermissionService}: tính toán quyền của tài khoản dựa trên
 * vai trò được gán và phạm vi (scope) tương ứng, đồng thời ghi log khi bị từ chối.
 */
@Service("permissionService")
public class PermissionServiceImpl implements PermissionService {
    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final ScopeRepository scopeRepository;
    private final AuditService auditService;

    public PermissionServiceImpl(AccountRepository accountRepository,
                                  AccountRoleRepository accountRoleRepository,
                                  RolePermissionRepository rolePermissionRepository,
                                  PermissionRepository permissionRepository,
                                  ScopeRepository scopeRepository,
                                  AuditService auditService) {
        this.accountRepository = accountRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
        this.scopeRepository = scopeRepository;
        this.auditService = auditService;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPermission(UUID accountId, String permissionCode) {
        return isActive(accountId) && readGrants(accountId).stream()
                .anyMatch(grant -> grant.permissionCode().equals(permissionCode));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAllowed(UUID accountId, String permissionCode, UUID branchId) {
        Objects.requireNonNull(branchId, "branchId must not be null");
        return isActive(accountId) && readGrants(accountId).stream()
                .filter(grant -> grant.permissionCode().equals(permissionCode))
                .anyMatch(grant -> grant.scope().getScopeType() == ScopeType.ALL_SYSTEM
                        || Objects.equals(grant.scope().getBranchId(), branchId));
    }

    /**
     * Đọc và tính toán các quyền (kèm phạm vi) hiện có của tài khoản từ
     * bản ghi gán vai trò, ánh xạ vai trò-quyền và phạm vi đang active.
     */
    private List<Grant> readGrants(UUID accountId) {
        List<AccountRole> assignments = accountRoleRepository
                .findEffectiveByAccountId(accountId, EntityStatus.ACTIVE, Instant.now());
        if (assignments.isEmpty()) {
            return List.of();
        }

        List<RolePermission> mappings = rolePermissionRepository.findByRoleIdIn(
                assignments.stream().map(AccountRole::getRoleId).distinct().toList());
        Map<UUID, Permission> permissions = permissionRepository.findAllById(
                        mappings.stream().map(RolePermission::getPermissionId).distinct().toList()
                ).stream().filter(permission -> permission.getStatus() == EntityStatus.ACTIVE)
                .collect(Collectors.toMap(Permission::getId, Function.identity()));
        Map<UUID, Scope> scopes = scopeRepository.findAllById(
                        assignments.stream().map(AccountRole::getScopeId).distinct().toList()
                ).stream().filter(scope -> scope.getStatus() == EntityStatus.ACTIVE)
                .collect(Collectors.toMap(Scope::getId, Function.identity()));

        return assignments.stream()
                .flatMap(assignment -> mappings.stream()
                        .filter(mapping -> mapping.getRoleId().equals(assignment.getRoleId()))
                        .map(RolePermission::getPermissionId)
                        .filter(permissions::containsKey)
                        .map(permissionId -> new Grant(permissions.get(permissionId).getCode(), scopes.get(assignment.getScopeId()))))
                .filter(grant -> grant.scope() != null)
                .toList();
    }

    @Override
    public void requirePermission(String permissionCode) {
        UUID accountId = currentAccountId();
        if (!hasPermission(accountId, permissionCode)) {
            auditDenied(accountId, AuditTargetType.PERMISSION, null, Map.of("permissionCode", permissionCode));
            throw new BaseException(ErrorCode.PERMISSION_DENIED);
        }
    }

    @Override
    public void requireAccess(String permissionCode, UUID branchId) {
        UUID accountId = currentAccountId();
        if (!isAllowed(accountId, permissionCode, branchId)) {
            auditDenied(accountId, AuditTargetType.SCOPE, branchId,
                    Map.of("permissionCode", permissionCode, "branchId", branchId.toString()));
            throw new BaseException(ErrorCode.CROSS_SCOPE_DENIED);
        }
    }

    private boolean isActive(UUID accountId) {
        return accountId != null && accountRepository.findById(accountId)
                .map(account -> account.getStatus() == EntityStatus.ACTIVE)
                .orElse(false);
    }

    private UUID currentAccountId() {
        return SecurityUtils.getCurrentAccountId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));
    }

    /**
     * Ghi nhận một sự kiện bị từ chối truy cập vào audit log.
     */
    private void auditDenied(UUID accountId, AuditTargetType targetType,
                              UUID targetId, Map<String, Object> details) {
        auditService.record(new AuditEvent(accountId, AuditAction.ACCESS_DENIED,
                AuditModule.SYS, targetType, targetId, details));
    }

    private record Grant(String permissionCode, Scope scope) {
    }
}
