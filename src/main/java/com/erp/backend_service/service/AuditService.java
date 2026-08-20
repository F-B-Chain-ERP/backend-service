package com.erp.backend_service.service;

import com.erp.backend_service.util.audit.AuditEvent;

/**
 * Ghi nhận các sự kiện hệ thống vào bảng lịch sử (audit log).
 */
public interface AuditService {

    /**
     * Lưu một sự kiện audit (hành động, đối tượng, ip, user-agent...) vào cơ sở dữ liệu.
     *
     * @param event sự kiện cần ghi nhận
     */
    void record(AuditEvent event);
}
