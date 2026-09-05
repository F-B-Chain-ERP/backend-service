package com.erp.backend_service.configuration;

import com.erp.backend_service.security.CustomUserDetails;
import com.erp.backend_service.security.SecurityUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

/**
 * Cấu hình JPA Auditing: cung cấp AuditorAware trả về username của tài khoản đang đăng nhập
 * để tự động điền vào các trường created_by và updated_by trong BaseAuditingEntity.
 */
@Configuration
public class AuditingConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> SecurityUtils.getCurrentUserDetails()
                .map(CustomUserDetails::getUsername)
                .or(() -> Optional.of("system"));
    }
}
