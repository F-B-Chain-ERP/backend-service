package com.erp.backend_service.security;

import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.repository.AccountRoleRepository;
import com.erp.backend_service.repository.PermissionRepository;
import com.erp.backend_service.repository.RolePermissionRepository;
import com.erp.backend_service.repository.RoleRepository;
import com.erp.core.domain.Account;
import com.erp.core.domain.AccountRole;
import com.erp.core.domain.Permission;
import com.erp.core.domain.Role;
import com.erp.core.domain.RolePermission;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(CustomUserDetailsService.class);

    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;

    public CustomUserDetailsService(
            AccountRepository accountRepository,
            AccountRoleRepository accountRoleRepository,
            RoleRepository roleRepository,
            RolePermissionRepository rolePermissionRepository,
            PermissionRepository permissionRepository
    ) {
        this.accountRepository = accountRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(@NonNull String usernameOrEmailOrId) throws UsernameNotFoundException {
        Account account = findAccount(usernameOrEmailOrId);
        if (account == null) {
            log.warn("Account not found for identifier: {}", usernameOrEmailOrId);
            throw new UsernameNotFoundException("Account not found: " + usernameOrEmailOrId);
        }

        // 1. Fetch active account roles
        List<AccountRole> accountRoles = accountRoleRepository.findByAccountIdAndStatus(account.getId(), "ACTIVE");
        List<UUID> roleIds = accountRoles.stream().map(AccountRole::getRoleId).distinct().toList();

        List<String> roleCodes = Collections.emptyList();
        List<String> permissionCodes = Collections.emptyList();

        if (!roleIds.isEmpty()) {
            // 2. Fetch role codes
            List<Role> roles = roleRepository.findAllById(roleIds);
            roleCodes = roles.stream()
                    .filter(r -> "ACTIVE".equalsIgnoreCase(r.getStatus()))
                    .map(Role::getCode)
                    .toList();

            // 3. Fetch permissions assigned to these roles
            List<RolePermission> rolePermissions = rolePermissionRepository.findByRoleIdIn(roleIds);
            List<UUID> permissionIds = rolePermissions.stream().map(RolePermission::getPermissionId).distinct().toList();

            if (!permissionIds.isEmpty()) {
                List<Permission> permissions = permissionRepository.findAllById(permissionIds);
                permissionCodes = permissions.stream()
                        .filter(p -> "ACTIVE".equalsIgnoreCase(p.getStatus()))
                        .map(Permission::getCode)
                        .toList();
            }
        }

        return CustomUserDetails.fromAccount(account, roleCodes, permissionCodes);
    }

    private Account findAccount(String identifier) {
        try {
            UUID id = UUID.fromString(identifier);
            Optional<Account> byId = accountRepository.findById(id);
            if (byId.isPresent()) {
                return byId.get();
            }
        } catch (IllegalArgumentException ignored) {
            // Not a UUID, search by username or email
        }
        return accountRepository.findByUsernameOrEmail(identifier, identifier).orElse(null);
    }
}
