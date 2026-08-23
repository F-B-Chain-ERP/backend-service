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
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.AccountRevocationService;
import com.erp.backend_service.service.AuditService;
import com.erp.backend_service.service.PermissionService;
import com.erp.backend_service.service.RoleService;
import com.erp.backend_service.service.ScopeService;
import com.erp.core.domain.AccountRole;
import com.erp.core.domain.Role;
import com.erp.core.domain.Scope;
import com.erp.core.dto.auth.RoleAssignmentRequest;
import com.erp.core.dto.auth.RoleAssignmentResponse;
import com.erp.core.dto.auth.RoleResponse;
import com.erp.core.dto.request.role.CreateRoleRequest;
import com.erp.core.dto.request.role.UpdateRoleRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.enums.EntityStatus;
import com.erp.core.enums.PrincipalType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Triển khai {@link RoleService}: gán/thu hồi vai trò cho tài khoản, kiểm tra
 * tính hợp lệ của tài khoản/vai trò/phạm vi và thu hồi token khi có thay đổi.
 */

@Service
public class RoleServiceImpl implements RoleService {
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final AuditService auditService;
    private final AccountRevocationService revocationService;
    private final PermissionService permissionService;
    private final ScopeService scopeService;
    private final RoleAssignmentMapper assignmentMapper;
    private final Duration accessTokenLifetime;

    public RoleServiceImpl(AccountRepository accountRepository,
                           RoleRepository roleRepository,
                           AccountRoleRepository accountRoleRepository,
                           AuditService auditService,
                           AccountRevocationService revocationService,
                           PermissionService permissionService,
                           ScopeService scopeService,
                           RoleAssignmentMapper assignmentMapper,
                             @Value("${app.jwt.access-token-expiry}") long accessTokenExpiry) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.auditService = auditService;
        this.revocationService = revocationService;
        this.permissionService = permissionService;
        this.scopeService = scopeService;
        this.assignmentMapper = assignmentMapper;
        this.accessTokenLifetime = Duration.ofSeconds(accessTokenExpiry);
    }
    /**
     * Gán vai trò cho tài khoản tại một phạm vi cụ thể.
     */
    /** {@inheritDoc} */
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
        permissionService.evictSnapshot(request.accountId());
        revocationService.revokeAccount(request.accountId(), accessTokenLifetime);
        return assignmentMapper.toResponse(accountRole, scopeService.getActive(request.scopeId()));
    }
    /**
     * Thu hồi vai trò khỏi tài khoản.
     */
    /** {@inheritDoc} */
    @Override
    @Transactional
    public void revoke(UUID assignmentId) {
        AccountRole accountRole = accountRoleRepository.findById(assignmentId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        verifyCanModify(accountRole.getAccountId());
        accountRole.setStatus(EntityStatus.INACTIVE);
        accountRoleRepository.save(accountRole);
        auditService.record(assignmentEvent(AuditAction.REVOKE_ROLE, accountRole));
        permissionService.evictSnapshot(accountRole.getAccountId());
        revocationService.revokeAccount(accountRole.getAccountId(), accessTokenLifetime);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<RoleAssignmentResponse> findByAccount(UUID accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new BaseException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        List<AccountRole> accountRoles = accountRoleRepository.findByAccountId(accountId);
        Map<UUID, Scope> scopes = scopeService.findAllById(accountRoles.stream().map(AccountRole::getScopeId).distinct().toList());
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
        scopeService.getActive(request.scopeId());
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
        accountRole.setAssignedBy(SecurityUtils.getCurrentPrincipalId()
                .map(UUID::toString).orElse("SYSTEM"));
        accountRole.setExpiresAt(request.expiresAt());
    }

    /** Tạo sự kiện audit cho hành động gán/thu hồi vai trò của một tài khoản. */
    private AuditEvent assignmentEvent(AuditAction action, AccountRole accountRole) {
        PrincipalType actorType = SecurityUtils.getCurrentPrincipalType().orElse(null);
        return new AuditEvent(
                actorType,
                SecurityUtils.getCurrentPrincipalId().orElse(null),
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

    /** Lấy phạm vi từ map, ném lỗi nếu bản ghi gán vai trò tham chiếu phạm vi không tồn tại. */
    private Scope requiredScope(Map<UUID, Scope> scopes, AccountRole accountRole) {
        Scope scope = scopes.get(accountRole.getScopeId());
        if (scope == null) {
            throw new IllegalStateException("AccountRole references a missing scope: " + accountRole.getId());
        }
        return scope;
    }

    @Override
    public RoleResponse create(CreateRoleRequest request) {
        String code = request.name().toUpperCase();
        if (roleRepository.findByCode(code).isPresent()) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        Role role = new Role();
        role.setCode(code);
        role.setName(request.name());
        role.setDescription(request.description());
        role.setRoleType(request.roleType());
        role.setStatus(request.status());

        Role saved = roleRepository.save(role);
        return toResponse(saved);
    }

    @Override
    public RoleResponse getById(UUID id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        return toResponse(role);
    }

    @Override
    public RoleResponse getByCode(String code) {
        Role role = roleRepository.findByCode(code)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));
        return toResponse(role);
    }

    @Override
    public PageResponse<RoleResponse> getAll(int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Role> rolePage;

        if (search != null && !search.trim().isEmpty()) {
            rolePage = roleRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(search, search, pageable);
        } else {
            rolePage = roleRepository.findAll(pageable);
        }

        return new PageResponse<>(
                rolePage.getNumber(),
                rolePage.getSize(),
                rolePage.getTotalElements(),
                rolePage.getTotalPages(),
                rolePage.getContent().stream().map(this::toResponse).toList()
        );
    }

    @Override
    public RoleResponse update(UUID id, UpdateRoleRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND));

        String code = request.name().toUpperCase();
        Optional<Role> existingByCode = roleRepository.findByCode(code);
        if (existingByCode.isPresent() && !existingByCode.get().getId().equals(id)) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        role.setName(request.name());
        role.setCode(code);
        role.setDescription(request.description());
        role.setRoleType(request.roleType());
        role.setStatus(request.status());

        Role updated = roleRepository.save(role);
        return toResponse(updated);
    }

    @Override
    public void delete(UUID id) {
        if (!roleRepository.existsById(id)) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        roleRepository.deleteById(id);
    }

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(
                role.getId().toString(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getRoleType(),
                role.getStatus()
        );
    }
}
