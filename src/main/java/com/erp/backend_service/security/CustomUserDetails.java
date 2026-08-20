package com.erp.backend_service.security;

import com.erp.core.domain.Account;
import com.erp.core.dto.auth.ScopeResponse;
import com.erp.core.enums.EntityStatus;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.*;

/**
 * Mở rộng {@link UserDetails} của Spring Security, lưu thêm accountId, thông tin
 * vai trò, quyền và phạm vi (scope) của tài khoản để dùng trong JWT và phân quyền.
 */
public class CustomUserDetails implements UserDetails {
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE_CODES = "roleCodes";
    private final UUID accountId;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final Collection<GrantedAuthority> authorities;
    private final List<String> roles;
    private final List<String> permissions;
    private final List<ScopeResponse> scopes;
    private final Instant issuedAt;

    public CustomUserDetails(
            UUID accountId,
            String username,
            String password,
            boolean enabled,
            Collection<GrantedAuthority> authorities,
            List<String> roles,
            List<String> permissions,
            List<ScopeResponse> scopes,
            Instant issuedAt
    ) {
        this.accountId = accountId;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        if (authorities != null) {
            this.authorities = authorities;
        } else {
            this.authorities = Collections.emptyList();
        }
        if (roles != null) {
            this.roles = roles;
        } else {
            this.roles = Collections.emptyList();
        }
        if (permissions != null) {
            this.permissions = permissions;
        } else {
            this.permissions = Collections.emptyList();
        }
        if (scopes != null) {
            this.scopes = scopes;
        } else {
            this.scopes = Collections.emptyList();
        }
        this.issuedAt = issuedAt;
    }

    private CustomUserDetails(
            UUID accountId,
            String username,
            boolean enabled,
            Collection<GrantedAuthority> authorities,
            List<String> roles,
            List<String> permissions,
            List<ScopeResponse> scopes,
            Instant issuedAt
    ) {
        this(accountId, username, null, enabled, authorities, roles, permissions, scopes, issuedAt);
    }

    /** Lấy id tài khoản. */
    public UUID getAccountId() {
        return accountId;
    }

    /** Lấy danh sách mã vai trò (đã có tiền tố ROLE_). */
    public List<String> getRoles() {
        return roles;
    }

    /** Lấy danh sách mã quyền của tài khoản. */
    public List<String> getPermissions() {
        return permissions;
    }

    /** Lấy danh sách phạm vi (scope) áp dụng cho tài khoản. */
    public List<ScopeResponse> getScopes() { return scopes; }

    /** Lấy thời điểm token được phát hành. */
    public Instant getIssuedAt() {
        return issuedAt;
    }

    /** Xây dựng UserDetails từ entity Account (dùng khi đăng nhập). */
    public static CustomUserDetails fromAccount(
            Account account,
            List<String> roleCodes,
            List<String> permissionCodes,
            List<ScopeResponse> scopes
    ) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        List<String> normalizedRoles = new ArrayList<>();
        if (roleCodes != null) {
            for (String role : roleCodes) {
                String roleAuthority;
                if (role.startsWith("ROLE_")) {
                    roleAuthority = role;
                } else {
                    roleAuthority = "ROLE_" + role;
                }
                authorities.add(new SimpleGrantedAuthority(roleAuthority));
                normalizedRoles.add(roleAuthority);
            }
        }

        List<String> normalizedPermissions = new ArrayList<>();
        if (permissionCodes != null) {
            for (String perm : permissionCodes) {
                authorities.add(new SimpleGrantedAuthority(perm));
                normalizedPermissions.add(perm);
            }
        }

        boolean isActive = account.getStatus() == EntityStatus.ACTIVE;

        return new CustomUserDetails(
                account.getId(),
                account.getUsername(),
                account.getPassword(),
                isActive,
                authorities,
                normalizedRoles,
                normalizedPermissions,
                scopes,
                Instant.now()
        );
    }

    /** Xây dựng UserDetails từ claims JWT và quyền/phạm vi trong Redis. */
    @SuppressWarnings("unchecked")
    public static CustomUserDetails fromClaims(Claims claims, PermissionSnapshot snapshot) {
        UUID accountId = UUID.fromString(claims.getSubject());
        String username = claims.get(CLAIM_USERNAME, String.class);
        List<String> roles = claims.get(CLAIM_ROLE_CODES, List.class);
        if (roles == null && snapshot != null) {
            roles = snapshot.roles();
        }
        List<String> permissions;
        List<ScopeResponse> scopes;
        if (snapshot == null) {
            permissions = Collections.emptyList();
            scopes = Collections.emptyList();
        } else {
            permissions = snapshot.permissions();
            scopes = snapshot.scopes();
        }

        Set<GrantedAuthority> authorities = new HashSet<>();
        if (roles != null) {
            for (String role : roles) {
                String authority;
                if (role.startsWith("ROLE_")) {
                    authority = role;
                } else {
                    authority = "ROLE_" + role;
                }
                authorities.add(new SimpleGrantedAuthority(authority));
            }
        }
        if (permissions != null) {
            for (String perm : permissions) {
                authorities.add(new SimpleGrantedAuthority(perm));
            }
        }

        Date iatDate = claims.getIssuedAt();
        Instant issuedAt;
        if (iatDate != null) {
            issuedAt = iatDate.toInstant();
        } else {
            issuedAt = Instant.now();
        }

        return new CustomUserDetails(
                accountId,
                username,
                true,
                authorities,
                roles != null ? roles : Collections.emptyList(),
                permissions != null ? permissions : Collections.emptyList(),
                scopes,
                issuedAt
        );
    }

    /** {@inheritDoc} */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /** {@inheritDoc} */
    @Override
    public String getPassword() {
        return password;
    }

    /** {@inheritDoc} */
    @Override
    public String getUsername() {
        return username;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
