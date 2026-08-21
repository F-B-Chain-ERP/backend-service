package com.erp.backend_service.security;

import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.repository.AccountRoleRepository;
import com.erp.backend_service.repository.PermissionRepository;
import com.erp.backend_service.repository.RolePermissionRepository;
import com.erp.backend_service.repository.RoleRepository;
import com.erp.backend_service.repository.ScopeRepository;
import com.erp.core.domain.Account;
import com.erp.core.domain.AccountRole;
import com.erp.core.domain.Permission;
import com.erp.core.domain.Role;
import com.erp.core.domain.RolePermission;
import com.erp.core.domain.Scope;
import com.erp.core.dto.auth.ScopeResponse;
import com.erp.core.enums.EntityStatus;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tải thông tin người dùng từ cơ sở dữ liệu để xây dựng {@link CustomUserDetails},
 * bao gồm các vai trò, quyền và phạm vi đang active của tài khoản.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final ScopeRepository scopeRepository;

    public CustomUserDetailsService(AccountRepository accountRepository,
                                    AccountRoleRepository accountRoleRepository,
                                    RoleRepository roleRepository,
                                    RolePermissionRepository rolePermissionRepository,
                                    PermissionRepository permissionRepository,
                                    ScopeRepository scopeRepository) {
        this.accountRepository = accountRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
        this.scopeRepository = scopeRepository;
    }

    /** Tải người dùng theo id hoặc username/email và xây dựng CustomUserDetails. */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(@NonNull String identifier) throws UsernameNotFoundException {
        Account account = findAccount(identifier);
        List<Grant> grants = readGrants(account.getId());
        return CustomUserDetails.fromAccount(
                account,
                grants.stream().map(Grant::roleCode).distinct().toList(),
                grants.stream().map(Grant::permissionCode).distinct().toList(),
                grants.stream().map(Grant::scope).distinct().toList()
        );
    }

    /** Đọc và tính toán các cặp (vai trò, quyền, phạm vi) đang active của tài khoản. */
    private List<Grant> readGrants(UUID accountId) {
        List<AccountRole> assignments = accountRoleRepository
                .findEffectiveByAccountId(accountId, EntityStatus.ACTIVE, Instant.now());
        if (assignments.isEmpty()) {
            return List.of();
        }

        Map<UUID, Role> roles = roleRepository.findAllById(
                        assignments.stream().map(AccountRole::getRoleId).distinct().toList()
                ).stream().filter(role -> role.getStatus() == EntityStatus.ACTIVE)
                .collect(Collectors.toMap(Role::getId, Function.identity()));
        Map<UUID, Scope> scopes = scopeRepository.findAllById(
                        assignments.stream().map(AccountRole::getScopeId).distinct().toList()
                ).stream().filter(scope -> scope.getStatus() == EntityStatus.ACTIVE)
                .collect(Collectors.toMap(Scope::getId, Function.identity()));
        List<RolePermission> mappings = rolePermissionRepository.findByRoleIdIn(roles.keySet());
        Map<UUID, Permission> permissions = permissionRepository.findAllById(
                        mappings.stream().map(RolePermission::getPermissionId).distinct().toList()
                ).stream().filter(permission -> permission.getStatus() == EntityStatus.ACTIVE)
                .collect(Collectors.toMap(Permission::getId, Function.identity()));

        return assignments.stream()
                .filter(assignment -> roles.containsKey(assignment.getRoleId()))
                .filter(assignment -> scopes.containsKey(assignment.getScopeId()))
                .flatMap(assignment -> mappings.stream()
                        .filter(mapping -> mapping.getRoleId().equals(assignment.getRoleId()))
                        .map(RolePermission::getPermissionId)
                        .filter(permissions::containsKey)
                        .map(permissionId -> toGrant(roles.get(assignment.getRoleId()),
                                permissions.get(permissionId), scopes.get(assignment.getScopeId()))))
                .toList();
    }

    /** Tạo bản ghi grant từ vai trò, quyền và phạm vi. */
    private Grant toGrant(Role role, Permission permission, Scope scope) {
        return new Grant(role.getCode(), permission.getCode(),
                new ScopeResponse(scope.getId(), scope.getScopeType(), scope.getBranchId()));
    }

    /** Tìm tài khoản theo UUID hoặc username/email. */
    private Account findAccount(String identifier) {
        try {
            return accountRepository.findById(UUID.fromString(identifier))
                    .orElseThrow(() -> notFound(identifier));
        } catch (IllegalArgumentException ignored) {
            return accountRepository.findByUsernameOrEmail(identifier, identifier)
                    .orElseThrow(() -> notFound(identifier));
        }
    }

    /** Tạo ngoại lệ tài khoản không tồn tại. */
    private UsernameNotFoundException notFound(String identifier) {
        return new UsernameNotFoundException("Account not found for supplied identifier");
    }

    /** Bản ghi tạm chứa một cặp (vai trò, quyền) kèm phạm vi tương ứng. */
    private record Grant(String roleCode, String permissionCode, ScopeResponse scope) {
    }
}
