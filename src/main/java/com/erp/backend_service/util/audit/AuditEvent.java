package com.erp.backend_service.util.audit;

import com.erp.core.enums.PrincipalType;

import java.util.Map;
import java.util.UUID;

/**
 * Sự kiện audit được tạo trong code và chuyển đổi thành bản ghi {@code AuditLog}.
 *
 * @param actorType  loại thực thể thực hiện hành động (ACCOUNT / CUSTOMER), có thể null với hệ thống
 * @param actorId    id thực thể thực hiện hành động (tương ứng với actorType)
 * @param action     loại hành động (đăng nhập, gán vai trò, từ chối...)
 * @param module     phân loại module (hệ thống...)
 * @param targetType loại đối tượng bị tác động (tài khoản, quyền, phạm vi)
 * @param targetId   id của đối tượng bị tác động (có thể null)
 * @param details    thông tin bổ sung dạng key-value
 */
public record AuditEvent(
        PrincipalType actorType,
        UUID actorId,
        AuditAction action,
        AuditModule module,
        AuditTargetType targetType,
        UUID targetId,
        Map<String, Object> details
) {
    public AuditEvent {
        if (details == null) {
            details = Map.of();
        } else {
            details = Map.copyOf(details);
        }
    }
}
