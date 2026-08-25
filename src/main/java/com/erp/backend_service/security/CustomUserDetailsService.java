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
import com.erp.core.enums.ScopeType;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    public UserDetails loadUserByUsername(@NonNull String identifier) throws UsernameNotFoundException {
        Account account = findAccount(identifier);

        List<AccountRole> assignments = accountRoleRepository
                .findEffectiveByAccountId(account.getId(), EntityStatus.ACTIVE, Instant.now());

        List<String> roleCodes = new java.util.ArrayList<>();
        List<String> permissionCodes = new java.util.ArrayList<>();
        List<ScopeResponse> scopes = new java.util.ArrayList<>();

        if (account.getPrimaryBranchId() != null) {
            scopeRepository.findByScopeTypeAndBranchId(ScopeType.STORE, account.getPrimaryBranchId())
                    .ifPresent(s -> scopes.add(new ScopeResponse(s.getId(), s.getScopeType(), s.getBranchId())));
        }

        if (!assignments.isEmpty()) {
            Map<UUID, Role> roles = roleRepository.findAllById(
                    assignments.stream().map(AccountRole::getRoleId).distinct().toList()
            ).stream().filter(role -> role.getStatus() == EntityStatus.ACTIVE)
            .collect(Collectors.toMap(Role::getId, Function.identity()));

            Map<UUID, Scope> scopeEntities = scopeRepository.findAllById(
                    assignments.stream().map(AccountRole::getScopeId).distinct().toList()
            ).stream().filter(scope -> scope.getStatus() == EntityStatus.ACTIVE)
            .collect(Collectors.toMap(Scope::getId, Function.identity()));

            roleCodes.addAll(assignments.stream()
                    .map(a -> roles.get(a.getRoleId()))
                    .filter(Objects::nonNull)
                    .map(Role::getCode)
                    .distinct()
                    .toList());

            List<ScopeResponse> assignedScopes = assignments.stream()
                    .map(a -> scopeEntities.get(a.getScopeId()))
                    .filter(Objects::nonNull)
                    .map(s -> new ScopeResponse(s.getId(), s.getScopeType(), s.getBranchId()))
                    .distinct()
                    .toList();
            scopes.addAll(assignedScopes);

            List<RolePermission> mappings = rolePermissionRepository.findByRoleIdIn(roles.keySet());
            Map<UUID, Permission> permissions = permissionRepository.findAllById(
                    mappings.stream().map(RolePermission::getPermissionId).distinct().toList()
            ).stream().filter(permission -> permission.getStatus() == EntityStatus.ACTIVE)
            .collect(Collectors.toMap(Permission::getId, Function.identity()));

            permissionCodes.addAll(mappings.stream()
                    .map(m -> permissions.get(m.getPermissionId()))
                    .filter(Objects::nonNull)
                    .map(Permission::getCode)
                    .distinct()
                    .toList());
        }

        return CustomUserDetails.fromAccount(
                account,
                roleCodes.stream().distinct().toList(),
                permissionCodes.stream().distinct().toList(),
                scopes.stream().distinct().toList()
        );
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
}
