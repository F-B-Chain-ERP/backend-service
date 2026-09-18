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

    /**
     * Kiểm tra xem người dùng hiện tại có vai trò quản trị viên toàn quyền (Superuser) hay không.
     * Áp dụng cho tài khoản 'admin', vai trò ADMIN/ROLE_ADMIN, hoặc quyền FULL_PERMISSION.
     */
    public boolean isAdmin() {
        if ("admin".equalsIgnoreCase(username)) {
            return true;
        }
        if (roles != null && roles.stream().anyMatch(r -> 
                "ADMIN".equalsIgnoreCase(r) || "ROLE_ADMIN".equalsIgnoreCase(r))) {
            return true;
        }
        if (permissions != null && (permissions.contains("FULL_PERMISSION") || permissions.contains("ROLE_ADMIN") || permissions.contains("ADMIN"))) {
            return true;
        }
        if (authorities != null && authorities.stream().anyMatch(a -> 
                "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()) ||
                "ADMIN".equalsIgnoreCase(a.getAuthority()) ||
                "FULL_PERMISSION".equalsIgnoreCase(a.getAuthority()))) {
            return true;
        }
        return false;
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
        List<String> normalizedPermissions = addAuthorities(permissionCodes, authorities);

        boolean isAdmin = "admin".equalsIgnoreCase(account.getUsername())
                || (roleCodes != null && roleCodes.stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r) || "ROLE_ADMIN".equalsIgnoreCase(r)));
        if (isAdmin) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            authorities.add(new SimpleGrantedAuthority("ADMIN"));
            authorities.add(new SimpleGrantedAuthority("FULL_PERMISSION"));
            if (!normalizedRoles.contains("ROLE_ADMIN")) normalizedRoles.add("ROLE_ADMIN");
            if (!normalizedRoles.contains("ADMIN")) normalizedRoles.add("ADMIN");
            if (!normalizedPermissions.contains("FULL_PERMISSION")) normalizedPermissions.add("FULL_PERMISSION");
            if (scopes == null) {
                scopes = new ArrayList<>();
            } else {
                scopes = new ArrayList<>(scopes);
            }
            if (scopes.stream().noneMatch(s -> s.scopeType() == ScopeType.ALL_SYSTEM)) {
                scopes.add(new ScopeResponse(UUID.fromString("d0000000-0000-0000-0000-000000000001"), ScopeType.ALL_SYSTEM, null));
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
                authorities.add(new SimpleGrantedAuthority(stripRolePrefix(perm)));
            }
        }
        if (principalType == PrincipalType.CUSTOMER) {
            authorities.add(new SimpleGrantedAuthority(ROLE_CUSTOMER));
        }

        boolean isAdmin = "admin".equalsIgnoreCase(username)
                || (roles != null && roles.stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r) || "ROLE_ADMIN".equalsIgnoreCase(r)))
                || (permissions != null && (permissions.contains("FULL_PERMISSION") || permissions.contains("ROLE_ADMIN")));
        if (isAdmin) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            authorities.add(new SimpleGrantedAuthority("ADMIN"));
            authorities.add(new SimpleGrantedAuthority("FULL_PERMISSION"));
            if (roles == null) {
                roles = new ArrayList<>();
            } else {
                roles = new ArrayList<>(roles);
            }
            if (!roles.contains("ROLE_ADMIN")) roles.add("ROLE_ADMIN");
            if (!roles.contains("ADMIN")) roles.add("ADMIN");

            if (permissions == null) {
                permissions = new ArrayList<>();
            } else {
                permissions = new ArrayList<>(permissions);
            }
            if (!permissions.contains("FULL_PERMISSION")) permissions.add("FULL_PERMISSION");

            if (scopes == null) {
                scopes = new ArrayList<>();
            } else {
                scopes = new ArrayList<>(scopes);
            }
            if (scopes.stream().noneMatch(s -> s.scopeType() == ScopeType.ALL_SYSTEM)) {
                scopes.add(new ScopeResponse(UUID.fromString("d0000000-0000-0000-0000-000000000001"), ScopeType.ALL_SYSTEM, null));
            }
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
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.split("\\|", -1);
        if (parts.length < 3) {
            return null;
        }
        try {
            UUID id = parts[0].isBlank() ? null : UUID.fromString(parts[0]);
            ScopeType scopeType = parts[1].isBlank() ? null : ScopeType.valueOf(parts[1]);
            UUID branchId = parts[2].isBlank() ? null : UUID.fromString(parts[2]);
            return new ScopeResponse(id, scopeType, branchId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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

    /** Thêm mã quyền (giữ nguyên, KHÔNG gắn tiền tố ROLE_) vào tập quyền và trả về danh sách. */
    private static List<String> addAuthorities(List<String> codes, Set<GrantedAuthority> authorities) {
        List<String> result = new ArrayList<>();
        if (codes == null) {
            return result;
        }
        for (String code : codes) {
            if (code == null) {
                continue;
            }
            authorities.add(new SimpleGrantedAuthority(code));
            result.add(code);
        }
        return result;
    }

    private static String withRolePrefix(String code) {
        return code.startsWith("ROLE_") ? code : "ROLE_" + code;
    }

    /** Loại bỏ tiền tố ROLE_ khỏi mã quyền (phòng snapshot cache lưu nhầm định dạng cũ). */
    private static String stripRolePrefix(String code) {
        return code.startsWith("ROLE_") ? code.substring("ROLE_".length()) : code;
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
