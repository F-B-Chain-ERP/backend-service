package com.erp.backend_service.security;

import com.erp.core.domain.Account;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.*;

public class CustomUserDetails implements UserDetails {

    private final UUID accountId;
    private final String username;
    private final String password;
    private final String fullName;
    private final String email;
    private final boolean enabled;
    private final Collection<GrantedAuthority> authorities;
    private final List<String> roles;
    private final List<String> permissions;
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
            Instant issuedAt
    ) {
        this.accountId = accountId;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.email = email;
        this.enabled = enabled;
        this.authorities = authorities != null ? authorities : Collections.emptyList();
        this.roles = roles != null ? roles : Collections.emptyList();
        this.permissions = permissions != null ? permissions : Collections.emptyList();
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

    public Instant getIssuedAt() {
        return issuedAt;
    }

    /**
     * Build UserDetails from database Account entity (used during Login).
     */
    public static CustomUserDetails fromAccount(
            Account account,
            List<String> roleCodes,
            List<String> permissionCodes
    ) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        List<String> normalizedRoles = new ArrayList<>();
        if (roleCodes != null) {
            for (String role : roleCodes) {
                String roleAuthority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
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

        boolean isActive = account.getStatus() == null || "ACTIVE".equalsIgnoreCase(account.getStatus());

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
                Instant.now()
        );
    }

    /**
     * Build UserDetails from JWT claims in the filter chain without database lookup.
     */
    @SuppressWarnings("unchecked")
    public static CustomUserDetails fromClaims(Claims claims) {
        UUID accountId = UUID.fromString(claims.getSubject());
        String username = claims.get("username", String.class);
        String fullName = claims.get("fullName", String.class);
        String email = claims.get("email", String.class);

        List<String> roles = claims.get("roles", List.class);
        List<String> permissions = claims.get("permissions", List.class);

        Set<GrantedAuthority> authorities = new HashSet<>();
        if (roles != null) {
            for (String role : roles) {
                authorities.add(new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role));
            }
        }
        if (permissions != null) {
            for (String perm : permissions) {
                authorities.add(new SimpleGrantedAuthority(perm));
            }
        }

        Date iatDate = claims.getIssuedAt();
        Instant issuedAt = iatDate != null ? iatDate.toInstant() : Instant.now();

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
                issuedAt
        );
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
