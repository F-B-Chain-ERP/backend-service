package com.erp.backend_service.exception;

/**
 * Ngoại lệ cho các trường hợp lỗi 400 Bad Request.
 */
public class BadRequestException extends BaseException {

    // Default 400 exception with custom message
    public BadRequestException(String message) {
        super(ErrorCode.BAD_REQUEST, message);
    }

    // specific 400 exception from predefined ErrorCode
    public BadRequestException(ErrorCode errorCode) {
        super(errorCode);
    }

    // specific 400 exception from predefined ErrorCode but overwrite the message
    public BadRequestException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
