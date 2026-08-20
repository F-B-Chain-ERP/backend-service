package com.erp.backend_service.security;

import com.erp.core.domain.Account;
import com.erp.core.dto.auth.ScopeResponse;
import com.erp.core.enums.ScopeType;
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
    private static final String CLAIM_FULL_NAME = "fullName";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "permissions";
    private static final String CLAIM_SCOPES = "scopes";

    private final UUID accountId;
    private final String username;
    private final String password;
    private final String fullName;
    private final String email;
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
            String fullName,
            String email,
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
        this.fullName = fullName;
        this.email = email;
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

    public UUID getAccountId() {
        return accountId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public List<String> getRoles() {
        return roles;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public List<ScopeResponse> getScopes() { return scopes; }

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
                account.getFullName(),
                account.getEmail(),
                isActive,
                authorities,
                normalizedRoles,
                normalizedPermissions,
                scopes,
                Instant.now()
        );
    }

    /** Xây dựng UserDetails từ claims của JWT (dùng trong filter, không truy vấn DB). */
    @SuppressWarnings("unchecked")
    public static CustomUserDetails fromClaims(Claims claims) {
        UUID accountId = UUID.fromString(claims.getSubject());
        String username = claims.get(CLAIM_USERNAME, String.class);
        String fullName = claims.get(CLAIM_FULL_NAME, String.class);
        String email = claims.get(CLAIM_EMAIL, String.class);

        List<String> roles = claims.get(CLAIM_ROLES, List.class);
        List<String> permissions = claims.get(CLAIM_PERMISSIONS, List.class);
        List<Map<String, Object>> scopeClaims = claims.get(CLAIM_SCOPES, List.class);
        List<ScopeResponse> scopes = scopeClaims == null ? Collections.emptyList() : scopeClaims.stream()
                .map(CustomUserDetails::scopeFromClaim)
                .filter(Objects::nonNull)
                .toList();

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
                null,
                fullName,
                email,
                true,
                authorities,
                roles != null ? roles : Collections.emptyList(),
                permissions != null ? permissions : Collections.emptyList(),
                scopes,
                issuedAt
        );
    }

    /** Chuyển đổi một claim phạm vi thành đối tượng ScopeResponse (null nếu lỗi). */
    private static ScopeResponse scopeFromClaim(Map<String, Object> claim) {
        try {
            UUID id = UUID.fromString(String.valueOf(claim.get("id")));
            ScopeType type = ScopeType.valueOf(String.valueOf(claim.get("scopeType")));
            Object branch = claim.get("branchId");
            UUID branchId;
            if (branch == null) {
                branchId = null;
            } else {
                branchId = UUID.fromString(String.valueOf(branch));
            }
            return new ScopeResponse(id, type, branchId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
