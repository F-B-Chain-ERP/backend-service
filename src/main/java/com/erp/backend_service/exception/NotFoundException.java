package com.erp.backend_service.exception;

/**
 * Exception for 404 Not Found cases.
 */
public class NotFoundException extends BaseException {

    // Default 404 exception with generic message
    public NotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    // specific 404 exception with resource name and identifier (e.g. User not found with identifier: 1)
    public NotFoundException(String resourceName, Object identifier) {
        super(ErrorCode.RESOURCE_NOT_FOUND, String.format("%s not found with identifier: %s", resourceName, identifier));
    }

    // Constructor to pass specific ErrorCode if needed
    public NotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
