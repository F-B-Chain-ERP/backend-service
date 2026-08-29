package com.erp.backend_service.security;

import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.util.RedisKeys;
import com.erp.core.dto.response.ApiResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filter giới hạn tốc độ truy cập (rate limit) theo tài khoản hoặc IP,
 * dùng Redis làm backend chính và Bucket4j cục bộ khi Redis không khả dụng.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;
    private final long anonymousCapacity;
    private final long anonymousRefillSeconds;
    private final long authenticatedCapacity;
    private final long authenticatedRefillSeconds;

    public RateLimitFilter(StringRedisTemplate stringRedisTemplate, JwtProvider jwtProvider,
                           ObjectMapper objectMapper,
                           @Value("${app.rate-limit.anonymous.capacity}") long anonymousCapacity,
                           @Value("${app.rate-limit.anonymous.refill-seconds}") long anonymousRefillSeconds,
                           @Value("${app.rate-limit.authenticated.capacity}") long authenticatedCapacity,
                           @Value("${app.rate-limit.authenticated.refill-seconds}") long authenticatedRefillSeconds) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jwtProvider = jwtProvider;
        this.objectMapper = objectMapper;
        this.anonymousCapacity = anonymousCapacity;
        this.anonymousRefillSeconds = anonymousRefillSeconds;
        this.authenticatedCapacity = authenticatedCapacity;
        this.authenticatedRefillSeconds = authenticatedRefillSeconds;
    }

    private final Map<String, Bucket> localBuckets = new ConcurrentHashMap<>();

    /** Áp dụng giới hạn tốc độ cho mỗi request, từ chối (429) nếu vượt quá. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/actuator");
    }

    /** Áp dụng giới hạn tốc độ cho mỗi request, từ chối (429) nếu vượt quá. */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String clientKey = resolveClientKey(request);
        boolean isAuthenticated = clientKey.startsWith("ratelimit:authenticated:");

        long capacity;
        long windowSeconds;
        if (isAuthenticated) {
            capacity = authenticatedCapacity;
            windowSeconds = authenticatedRefillSeconds;
        } else {
            capacity = anonymousCapacity;
            windowSeconds = anonymousRefillSeconds;
        }

        boolean allowed = checkRateLimit(clientKey, capacity, windowSeconds);

        if (!allowed) {
            log.warn("Rate limit exceeded for clientKey: {}", clientKey);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(windowSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            ApiResponse<Void> apiResponse = ApiResponse.error(
                    ErrorCode.TOO_MANY_REQUESTS.getStatusCode(),
                    ErrorCode.TOO_MANY_REQUESTS.getCode(),
                    ErrorCode.TOO_MANY_REQUESTS.getMessage()
            );
            response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** Kiểm tra và tăng bộ đếm truy cập trên Redis, trả về false nếu vượt quota. */
    private boolean checkRateLimit(String key, long capacity, long windowSeconds) {
        try {
            Long currentCount = stringRedisTemplate.opsForValue().increment(key);
            if (currentCount == null) {
                return true;
            }
            if (currentCount == 1L) {
                stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            } else if (currentCount != null && stringRedisTemplate.getExpire(key) < 0) {
                // Key tồn tại nhưng không có TTL (zombie) -> gắn lại để tránh khóa vĩnh viễn.
                stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }
            return currentCount <= capacity;
        } catch (Exception e) {
            log.warn("Redis unavailable for rate limiting, falling back to local Bucket4j: {}", e.getMessage());
            return checkLocalBucket(key, capacity, windowSeconds);
        }
    }

    /** Dự phòng giới hạn tốc độ cục bộ bằng Bucket4j khi Redis lỗi. */
    private boolean checkLocalBucket(String key, long capacity, long windowSeconds) {
        Bucket bucket = localBuckets.computeIfAbsent(key, k -> {
            Refill refill = Refill.greedy(capacity, Duration.ofSeconds(windowSeconds));
            Bandwidth limit = Bandwidth.classic(capacity, refill);
            return Bucket.builder().addLimit(limit).build();
        });
        return bucket.tryConsume(1);
    }

    /** Xác định khóa giới hạn: dùng accountId từ token nếu có, ngược lại dùng IP. */
    private String resolveClientKey(HttpServletRequest request) {
        // 1. Try to extract accountId from Bearer token
        Optional<String> tokenOpt = SecurityUtils.extractBearerToken(request);
        if (tokenOpt.isPresent()) {
            String token = tokenOpt.get();
            if (jwtProvider.validateToken(token) && jwtProvider.isAccessToken(token)) {
                try {
                    UUID accountId = jwtProvider.extractPrincipalId(token);
                    // Giới hạn theo phiên đăng nhập (jti) thay vì theo account,
                    // để nhiều người cùng dùng 1 tài khoản không cộng dồn vào chung 1 quota.
                    String jti = jwtProvider.extractAllClaims(token).getId();
                    return RedisKeys.rateLimitAuthenticated(accountId, jti != null ? jti : "session");
                } catch (Exception ignored) {
                }
            }
        }

        // 2. Fallback to client IP address
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip)) {
            ip = ip.split(",")[0].trim();
        } else {
            ip = request.getRemoteAddr();
        }

        String ipValue;
        if (ip != null) {
            ipValue = ip;
        } else {
            ipValue = "unknown";
        }
        return RedisKeys.rateLimitAnonymous(ipValue);
    }
}
