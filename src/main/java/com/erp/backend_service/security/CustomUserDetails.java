package com.erp.backend_service.security;

import com.erp.core.domain.Account;
import com.erp.core.domain.Customer;
import com.erp.core.dto.auth.ScopeResponse;
import com.erp.core.enums.EntityStatus;
import com.erp.core.enums.PrincipalType;
import com.erp.core.enums.ScopeType;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.*;

/**
 * Mở rộng {@link UserDetails} của Spring Security, lưu thêm principalId, loại
 * thực thể (ACCOUNT / CUSTOMER), thông tin vai trò, quyền và phạm vi (scope)
 * để dùng trong JWT và phân quyền.
 */
public class CustomUserDetails implements UserDetails {
    public static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";

    private final PrincipalType principalType;
    private final UUID principalId;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final Collection<GrantedAuthority> authorities;
    private final List<String> roles;
    private final List<String> permissions;
    private final List<ScopeResponse> scopes;
    private final UUID branchId;
    private final Instant issuedAt;

    public CustomUserDetails(
            PrincipalType principalType,
            UUID principalId,
            String username,
            String password,
            boolean enabled,
            Collection<GrantedAuthority> authorities,
            List<String> roles,
            List<String> permissions,
            List<ScopeResponse> scopes,
            UUID branchId,
            Instant issuedAt
    ) {
        this.principalType = principalType;
        this.principalId = principalId;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.authorities = authorities != null ? authorities : Collections.emptyList();
        this.roles = roles != null ? roles : Collections.emptyList();
        this.permissions = permissions != null ? permissions : Collections.emptyList();
        this.scopes = scopes != null ? scopes : Collections.emptyList();
        this.branchId = branchId;
        this.issuedAt = issuedAt;
    }

    /** Tạo bản sao thực thể với branchId (đơn vị đang thao tác) được thay thế. */
    public static CustomUserDetails withBranch(CustomUserDetails source, UUID branchId) {
        return new CustomUserDetails(
                source.principalType,
                source.principalId,
                source.username,
                source.password,
                source.enabled,
                source.authorities,
                source.roles,
                source.permissions,
                source.scopes,
                branchId,
                source.issuedAt
        );
    }

    /** Lấy loại thực thể sở hữu phiên (ACCOUNT / CUSTOMER). */
    public PrincipalType getPrincipalType() {
        return principalType;
    }

    /** Lấy id của thực thể sở hữu phiên (account hoặc customer). */
    public UUID getPrincipalId() {
        return principalId;
    }

    /** Lấy danh sách mã vai trò (đã có tiền tố ROLE_). */
    public List<String> getRoles() {
        return roles;
    }

    /** Lấy danh sách mã quyền của tài khoản. */
    public List<String> getPermissions() {
        return permissions;
    }

    /** Lấy danh sách phạm vi (scope) áp dụng. */
    public List<ScopeResponse> getScopes() {
        return scopes;
    }

    /** Lấy đơn vị (branch) đang được thao tác trong phiên hiện tại. */
    public UUID getBranchId() {
        return branchId;
    }

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
        List<String> normalizedRoles = normalize(roleCodes, authorities);
        List<String> normalizedPermissions = new ArrayList<>();
        if (permissionCodes != null) {
            for (String code : permissionCodes) {
                authorities.add(new SimpleGrantedAuthority(code));
                normalizedPermissions.add(code);
            }
        }

        boolean isActive = account.getStatus() == EntityStatus.ACTIVE;
        return new CustomUserDetails(
                PrincipalType.ACCOUNT,
                account.getId(),
                account.getUsername(),
                account.getPassword(),
                isActive,
                authorities,
                normalizedRoles,
                normalizedPermissions,
                scopes,
                null,
                Instant.now()
        );
    }

    /** Xây dựng UserDetails từ entity Customer (dùng khi đăng nhập). */
    public static CustomUserDetails fromCustomer(Customer customer) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority(ROLE_CUSTOMER));
        boolean isActive = customer.getStatus() == EntityStatus.ACTIVE;
        return new CustomUserDetails(
                PrincipalType.CUSTOMER,
                customer.getId(),
                customer.getEmail() != null ? customer.getEmail() : customer.getPhone(),
                customer.getPassword(),
                isActive,
                authorities,
                List.of(ROLE_CUSTOMER),
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                Instant.now()
        );
    }

    /** Xây dựng UserDetails từ claims JWT và quyền/phạm vi trong Redis. */
    @SuppressWarnings("unchecked")
    public static CustomUserDetails fromClaims(Claims claims, PermissionSnapshot snapshot) {
        PrincipalType principalType = readPrincipalType(claims);
        UUID principalId = UUID.fromString(claims.getSubject());
        String username = claims.get(JwtProvider.CLAIM_USERNAME, String.class);
        List<String> roles = claims.get(JwtProvider.CLAIM_ROLE_CODES, List.class);
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

        // Ưu tiên đọc scope/branch từ claim token; nếu thiếu thì dùng snapshot Redis.
        List<ScopeResponse> tokenScopes = readScopesFromClaims(claims);
        if (tokenScopes != null) {
            scopes = tokenScopes;
        }
        UUID branchId = readBranchIdFromClaims(claims);

        Set<GrantedAuthority> authorities = new HashSet<>();
        if (roles != null) {
            for (String role : roles) {
                authorities.add(new SimpleGrantedAuthority(withRolePrefix(role)));
            }
        }
        if (permissions != null) {
            for (String perm : permissions) {
                authorities.add(new SimpleGrantedAuthority(perm));
            }
        }
        if (principalType == PrincipalType.CUSTOMER) {
            authorities.add(new SimpleGrantedAuthority(ROLE_CUSTOMER));
        }

        Date iatDate = claims.getIssuedAt();
        Instant issuedAt = iatDate != null ? iatDate.toInstant() : Instant.now();

        return new CustomUserDetails(
                principalType,
                principalId,
                username,
                null,
                true,
                authorities,
                roles != null ? roles : Collections.emptyList(),
                permissions != null ? permissions : Collections.emptyList(),
                scopes,
                branchId,
                issuedAt
        );
    }

    /** Đọc danh sách phạm vi từ claim token (đã được JwtProvider mã hóa). */
    @SuppressWarnings("unchecked")
    private static List<ScopeResponse> readScopesFromClaims(Claims claims) {
        List<String> raw = claims.get(JwtProvider.CLAIM_SCOPES, List.class);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        List<ScopeResponse> result = new ArrayList<>();
        for (String value : raw) {
            ScopeResponse scope = parseScope(value);
            if (scope != null) {
                result.add(scope);
            }
        }
        return result.isEmpty() ? null : result;
    }

    /** Đọc branchId (đơn vị đang thao tác) từ claim token. */
    private static UUID readBranchIdFromClaims(Claims claims) {
        String value = claims.get(JwtProvider.CLAIM_BRANCH_ID, String.class);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Giải mã một chuỗi phạm vi (id|scopeType|branchId) thành ScopeResponse. */
    private static ScopeResponse parseScope(String value) {
        String[] parts = value.split("\\|", -1);
        if (parts.length < 3) {
            return null;
        }
        UUID branchId = parts[2].isBlank() ? null : UUID.fromString(parts[2]);
        return new ScopeResponse(UUID.fromString(parts[0]), ScopeType.valueOf(parts[1]), branchId);
    }

    private static PrincipalType readPrincipalType(Claims claims) {
        String value = claims.get(JwtProvider.CLAIM_PRINCIPAL_TYPE, String.class);
        if (value == null) {
            return PrincipalType.ACCOUNT;
        }
        return PrincipalType.valueOf(value);
    }

    private static List<String> normalize(List<String> codes, Set<GrantedAuthority> authorities) {
        List<String> result = new ArrayList<>();
        if (codes == null) {
            return result;
        }
        for (String code : codes) {
            String authority = withRolePrefix(code);
            authorities.add(new SimpleGrantedAuthority(authority));
            result.add(authority);
        }
        return result;
    }

    private static String withRolePrefix(String code) {
        return code.startsWith("ROLE_") ? code : "ROLE_" + code;
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
