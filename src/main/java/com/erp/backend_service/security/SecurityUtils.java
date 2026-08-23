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

/**
 * Tiện ích truy xuất thông tin xác thực hiện tại từ {@code SecurityContextHolder}
 * (accountId, username, quyền, vai trò) và trích xuất Bearer token từ request.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** Lấy id thực thể đang xác thực (account hoặc customer), rỗng nếu chưa đăng nhập. */
    public static Optional<UUID> getCurrentPrincipalId() {
        return getCurrentUserDetails().map(CustomUserDetails::getPrincipalId);
    }

    /** Lấy loại thực thể đang xác thực (ACCOUNT / CUSTOMER), rỗng nếu chưa đăng nhập. */
    public static Optional<com.erp.core.enums.PrincipalType> getCurrentPrincipalType() {
        return getCurrentUserDetails().map(CustomUserDetails::getPrincipalType);
    }

    /** Lấy đối tượng CustomUserDetails của người dùng hiện tại (nếu có). */
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

    /** Kiểm tra người dùng hiện tại có quyền (authority) được chỉ định hay không. */
    public static boolean hasAuthority(String authority) {
        return getCurrentUserDetails()
                .map(u -> u.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).filter(Objects::nonNull)
                        .anyMatch(a -> a.equals(authority)))
                .orElse(false);
    }

    /** Kiểm tra người dùng hiện tại có mã quyền được chỉ định hay không. */
    public static boolean hasPermission(String permissionCode) {
        return hasAuthority(permissionCode);
    }

    /** Trích xuất Bearer token từ header Authorization, rỗng nếu không có. */
    public static Optional<String> extractBearerToken(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return Optional.of(header.substring("Bearer ".length()).trim());
        }
        return Optional.empty();
    }
}
