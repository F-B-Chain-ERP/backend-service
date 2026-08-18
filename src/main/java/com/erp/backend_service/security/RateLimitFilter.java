package com.erp.backend_service.security;

import com.erp.backend_service.exception.ErrorCode;
import com.erp.core.dto.response.ApiResponse;
import tools.jackson.databind.ObjectMapper;
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

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(StringRedisTemplate stringRedisTemplate, JwtProvider jwtProvider, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jwtProvider = jwtProvider;
        this.objectMapper = objectMapper;
    }

    @Value("${app.rate-limit.anonymous.capacity:20}")
    private long anonCapacity;

    @Value("${app.rate-limit.anonymous.refill-seconds:60}")
    private long anonRefillSeconds;

    @Value("${app.rate-limit.authenticated.capacity:100}")
    private long authCapacity;

    @Value("${app.rate-limit.authenticated.refill-seconds:60}")
    private long authRefillSeconds;

    private final Map<String, Bucket> localBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String clientKey = resolveClientKey(request);
        boolean isAuthenticated = clientKey.startsWith("rl:user:");

        long capacity = isAuthenticated ? authCapacity : anonCapacity;
        long windowSeconds = isAuthenticated ? authRefillSeconds : anonRefillSeconds;

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

    private boolean checkRateLimit(String key, long capacity, long windowSeconds) {
        try {
            Long currentCount = stringRedisTemplate.opsForValue().increment(key);
            if (currentCount == null) {
                return true;
            }
            if (currentCount == 1L) {
                stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }
            return currentCount <= capacity;
        } catch (Exception e) {
            log.warn("Redis unavailable for rate limiting, falling back to local Bucket4j: {}", e.getMessage());
            return checkLocalBucket(key, capacity, windowSeconds);
        }
    }

    private boolean checkLocalBucket(String key, long capacity, long windowSeconds) {
        Bucket bucket = localBuckets.computeIfAbsent(key, k -> {
            Refill refill = Refill.greedy(capacity, Duration.ofSeconds(windowSeconds));
            Bandwidth limit = Bandwidth.classic(capacity, refill);
            return Bucket.builder().addLimit(limit).build();
        });
        return bucket.tryConsume(1);
    }

    private String resolveClientKey(HttpServletRequest request) {
        // 1. Try to extract accountId from Bearer token
        Optional<String> tokenOpt = SecurityUtils.extractBearerToken(request);
        if (tokenOpt.isPresent()) {
            String token = tokenOpt.get();
            if (jwtProvider.validateToken(token) && jwtProvider.isAccessToken(token)) {
                try {
                    UUID accountId = jwtProvider.extractAccountId(token);
                    return "rl:user:" + accountId;
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

        return "rl:ip:" + (ip != null ? ip : "unknown");
    }
}
