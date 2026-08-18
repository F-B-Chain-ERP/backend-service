package com.erp.backend_service.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * Get current authenticated user's account ID.
     */
    public static Optional<UUID> getCurrentAccountId() {
        return getCurrentUserDetails().map(CustomUserDetails::getAccountId);
    }

    /**
     * Get current authenticated username.
     */
    public static Optional<String> getCurrentUsername() {
        return getCurrentUserDetails().map(CustomUserDetails::getUsername);
    }

    /**
     * Get the current CustomUserDetails principal.
     */
    public static Optional<CustomUserDetails> getCurrentUserDetails() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof CustomUserDetails customUserDetails) {
            return Optional.of(customUserDetails);
        }
        return Optional.empty();
    }

    /**
     * Check whether current user has the specified authority.
     */
    public static boolean hasAuthority(String authority) {
        return getCurrentUserDetails()
                .map(u -> u.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).filter(Objects::nonNull)
                        .anyMatch(a -> a.equals(authority)))
                .orElse(false);
    }

    /**
     * Check whether current user has the specified role (e.g. "ADMIN" or "ROLE_ADMIN").
     */
    public static boolean hasRole(String role) {
        String roleName = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return hasAuthority(roleName);
    }

    /**
     * Check whether current user has the specified permission code.
     */
    public static boolean hasPermission(String permissionCode) {
        return hasAuthority(permissionCode);
    }

    /**
     * Extract Bearer token from the HTTP Authorization header.
     */
    public static Optional<String> extractBearerToken(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return Optional.of(header.substring(7).trim());
        }
        return Optional.empty();
    }
}
