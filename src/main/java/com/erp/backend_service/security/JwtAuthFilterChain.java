package com.erp.backend_service.security;

import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.service.AccountRevocationService;
import com.erp.backend_service.service.PermissionService;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.enums.PrincipalType;
import tools.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Filter xác thực JWT: trích xuất Bearer token, kiểm tra loại token, phát hành
 * và thu hồi, sau đó thiết lập thông tin xác thực vào SecurityContext.
 */
@Component
public class JwtAuthFilterChain extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilterChain.class);

    private final JwtProvider jwtProvider;
    private final AccountRevocationService accountRevocationService;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;

    public JwtAuthFilterChain(
            JwtProvider jwtProvider,
            AccountRevocationService accountRevocationService,
            PermissionService permissionService,
            ObjectMapper objectMapper
    ) {
        this.jwtProvider = jwtProvider;
        this.accountRevocationService = accountRevocationService;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
    }

    /** Path public của kênh bán hàng: không cần snapshot quyền. */
    private static boolean isSalesPath(String uri) {
        return uri != null && (uri.equals("/api/v1/sales") || uri.startsWith("/api/v1/sales/"));
    }

    /** Xử lý xác thực cho mỗi request: parse token, kiểm tra thu hồi, set context. */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        Optional<String> tokenOpt = SecurityUtils.extractBearerToken(request);

        if (tokenOpt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = tokenOpt.get();

        try {
            Claims claims = jwtProvider.extractAllClaims(token);
            if (!JwtProvider.TOKEN_TYPE_ACCESS.equals(claims.get("type", String.class))) {
                filterChain.doFilter(request, response);
                return;
            }
            PrincipalType principalType = jwtProvider.extractPrincipalType(token);
            UUID principalId = UUID.fromString(claims.getSubject());
            Date iatDate = claims.getIssuedAt();
            if (iatDate == null) {
                throw new io.jsonwebtoken.JwtException("Missing issued-at claim");
            }
            Instant issuedAt = iatDate.toInstant();

            if (principalType == PrincipalType.ACCOUNT
                    && accountRevocationService.isRevoked(principalId, issuedAt)) {
                log.warn("Access token for account {} issued at {} has been revoked", principalId, issuedAt);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");

                ApiResponse<Void> apiResponse = ApiResponse.error(
                        ErrorCode.TOKEN_REVOKED.getStatusCode(),
                        ErrorCode.TOKEN_REVOKED.getCode(),
                        ErrorCode.TOKEN_REVOKED.getMessage()
                );
                response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
                return;
            }

            // Kênh sales công khai, không check phân quyền: khỏi tải snapshot
            // (tiết kiệm 1 round-trip Redis qua WAN mỗi request). Authorities vẫn
            // lấy từ JWT claims nên @PreAuthorize không ảnh hưởng; snapshot sẽ
            // lazy-load khi requirePermission/requireAccess được gọi (sales không gọi).
            PermissionSnapshot snapshot = principalType == PrincipalType.ACCOUNT
                    && !isSalesPath(request.getRequestURI())
                    ? permissionService.getSnapshot(principalId) : null;
            CustomUserDetails userDetails = CustomUserDetails.fromClaims(claims, snapshot);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            log.error("Failed to set user authentication from JWT: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
