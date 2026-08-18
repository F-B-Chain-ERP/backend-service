package com.erp.backend_service.exception;

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
    BAD_CREDENTIALS(401, "ERR_401_BAD_CREDENTIALS", "Invalid username or password");

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
