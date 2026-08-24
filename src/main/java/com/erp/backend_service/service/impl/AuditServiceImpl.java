package com.erp.backend_service.service.impl;

import com.erp.backend_service.repository.AuditLogRepository;
import com.erp.backend_service.service.AuditService;
import com.erp.backend_service.util.audit.AuditEvent;
import com.erp.core.domain.AuditLog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


/**
 * Triển khai {@link AuditService}: chuyển đổi {@link com.erp.backend_service.util.audit.AuditEvent}
 * thành bản ghi {@code AuditLog} và lưu xuống cơ sở dữ liệu kèm ip/user-agent.
 */
@Service
public class AuditServiceImpl implements AuditService {
    private final AuditLogRepository repository;

    public AuditServiceImpl(AuditLogRepository repository) { this.repository = repository; }

    @Override
    @Transactional
    public void record(AuditEvent event) {
        AuditLog log = new AuditLog();
        log.setStatus(com.erp.core.enums.EntityStatus.ACTIVE);
        log.setActorType(event.actorType());
        log.setActorId(event.actorId());
        log.setAction(event.action().name());
        log.setModule(event.module().name());
        log.setTargetType(event.targetType() != null ? event.targetType().name() : null);
        log.setTargetId(event.targetId());
        log.setAfterData(event.details());
        HttpServletRequest request = currentRequest();
        if (request != null) {
            log.setIpAddress(clientIp(request));
            log.setUserAgent(request.getHeader("User-Agent"));
        }
        repository.save(log);
    }

    /**
     * Lấy {@link HttpServletRequest} hiện tại nếu đang trong ngữ cảnh web.
     */
    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    /**
     * Trích xuất địa chỉ IP thực của client, ưu tiên header {@code X-Forwarded-For}.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }
        return forwarded.split(",")[0].trim();
    }
}
