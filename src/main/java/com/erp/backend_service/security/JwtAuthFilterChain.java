package com.erp.backend_service.security;

import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.service.AccountRevocationService;
import com.erp.core.dto.response.ApiResponse;
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

@Component
public class JwtAuthFilterChain extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilterChain.class);

    private final JwtProvider jwtProvider;
    private final AccountRevocationService accountRevocationService;
    private final ObjectMapper objectMapper;

    public JwtAuthFilterChain(
            JwtProvider jwtProvider,
            AccountRevocationService accountRevocationService,
            ObjectMapper objectMapper
    ) {
        this.jwtProvider = jwtProvider;
        this.accountRevocationService = accountRevocationService;
        this.objectMapper = objectMapper;
    }

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

        if (!jwtProvider.validateToken(token) || !jwtProvider.isAccessToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtProvider.extractAllClaims(token);
            UUID accountId = UUID.fromString(claims.getSubject());
            Date iatDate = claims.getIssuedAt();
            Instant issuedAt = iatDate != null ? iatDate.toInstant() : Instant.now();

            if (accountRevocationService.isRevoked(accountId, issuedAt)) {
                log.warn("Access token for account {} issued at {} has been revoked", accountId, issuedAt);
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

            CustomUserDetails userDetails = CustomUserDetails.fromClaims(claims);
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
