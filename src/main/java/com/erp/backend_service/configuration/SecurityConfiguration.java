package com.erp.backend_service.configuration;

import com.erp.backend_service.repository.PermissionRepository;
import com.erp.backend_service.security.CustomUserDetailsService;
import com.erp.backend_service.security.JwtAccessDeniedHandler;
import com.erp.backend_service.security.JwtAuthFilterChain;
import com.erp.backend_service.security.JwtAuthenticationEntryPoint;
import com.erp.backend_service.security.RateLimitFilter;
import com.erp.core.domain.Permission;
import com.erp.core.enums.EntityStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
/**
 * Cấu hình bảo mật: vô hiệu hóa CSRF, phi trạng thái (stateless), CORS,
 * endpoint công khai, chuỗi filter JWT/rate-limit và mã hóa mật khẩu BCrypt.
 */
public class SecurityConfiguration {

    private final JwtAuthFilterChain jwtAuthFilterChain;
    private final RateLimitFilter rateLimitFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final CustomUserDetailsService customUserDetailsService;

    public SecurityConfiguration(
            JwtAuthFilterChain jwtAuthFilterChain,
            RateLimitFilter rateLimitFilter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            JwtAccessDeniedHandler jwtAccessDeniedHandler,
            CustomUserDetailsService customUserDetailsService
    ) {
        this.jwtAuthFilterChain = jwtAuthFilterChain;
        this.rateLimitFilter = rateLimitFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
        this.customUserDetailsService = customUserDetailsService;
    }

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh-token",
            "/api/v1/auth/oauth2/google",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-otp",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/notifications/sse",
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api/v1/sales/**",
            "/api/v1/public/**"
    };

    /**
     * Khởi tạo SecurityFilterChain với các chính sách bảo mật đã cấu hình.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/menu/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/menu/products/**").permitAll()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilterChain, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, JwtAuthFilterChain.class)
                .build();
    }

    /**
     * Cung cấp AuthenticationProvider sử dụng CustomUserDetailsService và BCrypt.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * Lấy AuthenticationManager từ cấu hình xác thực của Spring.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) {
        return config.getAuthenticationManager();
    }

    /**
     * Cung cấp BCryptPasswordEncoder (độ mạnh 12) để mã hóa mật khẩu.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Cấu hình nguồn CORS áp dụng cho toàn bộ endpoint.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Cung cấp RoleHierarchy cho Spring Security:
     * Tự động mở rộng quyền hạn của ADMIN (ROLE_ADMIN, ADMIN, FULL_PERMISSION)
     * thành toàn bộ danh mục mã quyền trong hệ thống.
     */
    @Bean
    public RoleHierarchy roleHierarchy(PermissionRepository permissionRepository) {
        return authorities -> {
            Set<GrantedAuthority> result = new HashSet<>(authorities);
            boolean isAdmin = authorities.stream().anyMatch(a ->
                    "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority())
                    || "ADMIN".equalsIgnoreCase(a.getAuthority())
                    || "FULL_PERMISSION".equalsIgnoreCase(a.getAuthority()));
            if (isAdmin) {
                result.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                result.add(new SimpleGrantedAuthority("ADMIN"));
                result.add(new SimpleGrantedAuthority("FULL_PERMISSION"));
                try {
                    List<Permission> permissions = permissionRepository.findByStatus(EntityStatus.ACTIVE);
                    for (Permission p : permissions) {
                        result.add(new SimpleGrantedAuthority(p.getCode()));
                    }
                } catch (Exception ignored) {
                }
            }
            return result;
        };
    }
}
