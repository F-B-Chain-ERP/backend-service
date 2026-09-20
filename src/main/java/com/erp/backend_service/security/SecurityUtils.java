package com.erp.backend_service.security;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.core.enums.PrincipalType;
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
    public static Optional<PrincipalType> getCurrentPrincipalType() {
        return getCurrentUserDetails().map(CustomUserDetails::getPrincipalType);
    }

    /** Trả về UUID của CUSTOMER đang login, ném UNAUTHORIZED nếu không phải CUSTOMER. */
    public static UUID requireCustomerId() {
        if (getCurrentPrincipalType().orElse(null) != PrincipalType.CUSTOMER) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Chỉ CUSTOMER được truy cập.");
        }
        return getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));
    }


    /** Lấy id chi nhánh đang làm việc hiện tại từ UserDetails trong SecurityContext. */
    public static Optional<UUID> getCurrentBranchId() {
        return getCurrentUserDetails().map(CustomUserDetails::getBranchId);
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

    /** Kiểm tra người dùng hiện tại có quyền (authority) được chỉ định hay không (Admin toàn quyền luôn trả về true). */
    public static boolean hasAuthority(String authority) {
        return getCurrentUserDetails()
                .map(u -> u.isAdmin() || u.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).filter(Objects::nonNull)
                        .anyMatch(a -> a.equals(authority)
                                || "FULL_PERMISSION".equals(a)
                                || "ROLE_ADMIN".equals(a)
                                || "ADMIN".equals(a)))
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
