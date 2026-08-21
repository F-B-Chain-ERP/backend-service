package com.erp.backend_service.util.audit;

/** Các loại hành động được ghi nhận trong lịch sử hệ thống (audit log). */
public enum AuditAction {
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    ASSIGN_ROLE,
    REVOKE_ROLE,
    ACCESS_DENIED
}
