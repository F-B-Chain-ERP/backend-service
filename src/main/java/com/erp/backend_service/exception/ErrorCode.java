package com.erp.backend_service.exception;

/**
 * Định nghĩa tập hợp các mã lỗi nghiệp vụ dùng chung trong hệ thống,
 * mỗi mã gồm HTTP status, code và thông báo mặc định.
 */
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(500, "ERR_500", "Uncategorized error"),
    INVALID_KEY(400, "ERR_400", "Invalid key"),
    BAD_REQUEST(400, "ERR_400_BAD_REQUEST", "Bad request"),
    RESOURCE_NOT_FOUND(404, "ERR_404_NOT_FOUND", "Resource not found"),
    USER_EXISTED(400, "ERR_400_USER_EXISTED", "User already existed"),
    USER_NOT_EXISTED(404, "ERR_404_USER_NOT_EXISTED", "User not existed"),
    UNAUTHENTICATED(401, "ERR_401", "Unauthenticated"),
    UNAUTHORIZED(403, "ERR_403", "You do not have permission"),
    INVALID_TOKEN(401, "ERR_401_INVALID_TOKEN", "Token is invalid or expired"),
    TOKEN_REVOKED(401, "ERR_401_TOKEN_REVOKED", "Token has been revoked"),
    ACCOUNT_DISABLED(403, "ERR_403_ACCOUNT_DISABLED", "Account is disabled or suspended"),
    TOO_MANY_REQUESTS(429, "ERR_429", "Too many requests, please slow down"),
    BAD_CREDENTIALS(401, "ERR_401_BAD_CREDENTIALS", "Invalid username or password"),
    ACCOUNT_LOCKED(401, "ACCOUNT_LOCKED", "Account is temporarily locked"),
    ASSIGNMENT_REQUIRED_FIELDS(400, "ASSIGNMENT_REQUIRED_FIELDS", "Account, role and scope are required"),
    ASSIGNMENT_EXISTS(409, "ASSIGNMENT_EXISTS", "Role and scope assignment already exists"),
    ACCOUNT_NOT_FOUND(404, "ACCOUNT_NOT_FOUND", "Account not found"),
    ACCOUNT_INACTIVE(401, "ACCOUNT_INACTIVE", "Account is inactive"),
    ROLE_NOT_FOUND(404, "ROLE_NOT_FOUND", "Role not found"),
    SCOPE_NOT_FOUND(404, "SCOPE_NOT_FOUND", "Scope not found"),
    PERMISSION_DENIED(403, "PERMISSION_DENIED", "Permission denied"),
    CROSS_SCOPE_DENIED(403, "CROSS_SCOPE_DENIED", "Data is outside the assigned scope"),
    CANNOT_MODIFY_ADMIN(400, "CANNOT_MODIFY_ADMIN", "Root administrator assignment cannot be modified"),
    ACCOUNT_SESSION_REVOKED(401, "ACCOUNT_SESSION_REVOKED", "Account session has been revoked"),
    INTERNAL_ERROR(500, "INTERNAL_ERROR", "Internal service error");

    private final int statusCode;
    private final String code;
    private final String message;

    ErrorCode(int statusCode, String code, String message) {
        this.statusCode = statusCode;
        this.code = code;
        this.message = message;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
