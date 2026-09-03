package com.erp.backend_service.exception;

import com.erp.core.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Tập trung xử lý ngoại lệ toàn cục, chuyển đổi các lỗi thành ApiResponse chuẩn
 * (mã lỗi, thông báo) với HTTP status tương ứng.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Xử lý các ngoại lệ không được phân loại (500). */
    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<ApiResponse<Void>> handlingRuntimeException(Exception exception) {
        // Ghi log stack trace để dễ dàng truy vết nguyên nhân thật (vd: lỗi Redis/DB).
        log.error("Lỗi không phân loại (ERR_500): {}", exception.getMessage(), exception);
        ApiResponse<Void> apiResponse = ApiResponse.error(
                ErrorCode.UNCATEGORIZED_EXCEPTION.getStatusCode(),
                ErrorCode.UNCATEGORIZED_EXCEPTION.getCode(),
                ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(apiResponse);
    }

    /** Xử lý ngoại lệ nghiệp vụ kế thừa BaseException (dùng mã lỗi tương ứng). */
    @ExceptionHandler(value = BaseException.class)
    public ResponseEntity<ApiResponse<Void>> handlingBaseException(BaseException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        ApiResponse<Void> apiResponse = ApiResponse.error(
                errorCode.getStatusCode(),
                errorCode.getCode(),
                exception.getMessage() != null ? exception.getMessage() : errorCode.getMessage()
        );
        return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
    }

    /** Xử lý sai thông tin đăng nhập (401). */
    @ExceptionHandler(value = BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handlingBadCredentials(BadCredentialsException exception) {
        ApiResponse<Void> apiResponse = ApiResponse.error(
                ErrorCode.BAD_CREDENTIALS.getStatusCode(),
                ErrorCode.BAD_CREDENTIALS.getCode(),
                ErrorCode.BAD_CREDENTIALS.getMessage()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(apiResponse);
    }

    /** Xử lý tài khoản bị vô hiệu hóa (403). */
    @ExceptionHandler(value = DisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handlingAccountDisabled(DisabledException exception) {
        ApiResponse<Void> apiResponse = ApiResponse.error(
                ErrorCode.ACCOUNT_DISABLED.getStatusCode(),
                ErrorCode.ACCOUNT_DISABLED.getCode(),
                ErrorCode.ACCOUNT_DISABLED.getMessage()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(apiResponse);
    }

    /** Xử lý tài khoản bị khóa tạm thời (401). */
    @ExceptionHandler(value = LockedException.class)
    public ResponseEntity<ApiResponse<Void>> handlingAccountLocked(LockedException exception) {
        ErrorCode code = ErrorCode.ACCOUNT_LOCKED;
        return ResponseEntity.status(code.getStatusCode()).body(
                ApiResponse.error(code.getStatusCode(), code.getCode(), code.getMessage())
        );
    }

    /** Xử lý các lỗi xác thực chung (401). */
    @ExceptionHandler(value = AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handlingAuthenticationException(AuthenticationException exception) {
        ApiResponse<Void> apiResponse = ApiResponse.error(
                ErrorCode.UNAUTHENTICATED.getStatusCode(),
                ErrorCode.UNAUTHENTICATED.getCode(),
                exception.getMessage() != null ? exception.getMessage() : ErrorCode.UNAUTHENTICATED.getMessage()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(apiResponse);
    }

    /** Xử lý truy cập bị từ chối do thiếu quyền (403). */
    @ExceptionHandler(value = AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handlingAccessDeniedException(AccessDeniedException exception) {
        ApiResponse<Void> apiResponse = ApiResponse.error(
                ErrorCode.UNAUTHORIZED.getStatusCode(),
                ErrorCode.UNAUTHORIZED.getCode(),
                ErrorCode.UNAUTHORIZED.getMessage()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(apiResponse);
    }

    /** Xử lý lỗi validate request (400), trả về chi tiết từng trường không hợp lệ. */
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handlingValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new HashMap<>();
        exception.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ApiResponse<Map<String, String>> apiResponse = new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                "ERR_VALIDATION",
                "Validation error",
                errors,
                Instant.now()
        );

        return ResponseEntity.badRequest().body(apiResponse);
    }

    /** Xử lý lỗi validate ở header/path/query param (400). */
    @ExceptionHandler(value = ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handlingConstraintViolation(ConstraintViolationException exception) {
        Map<String, String> errors = new HashMap<>();
        exception.getConstraintViolations().forEach(violation -> {
            String fieldName = violation.getPropertyPath().toString();
            errors.put(fieldName, violation.getMessage());
        });
        return ResponseEntity.badRequest().body(validationResponse(errors));
    }

    /** Xử lý thiếu header bắt buộc (400). */
    @ExceptionHandler(value = MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handlingMissingRequestHeader(MissingRequestHeaderException exception) {
        Map<String, String> errors = new HashMap<>();
        errors.put(exception.getHeaderName(), exception.getHeaderName() + " header is required");
        return ResponseEntity.badRequest().body(validationResponse(errors));
    }

    /** Xử lý tham số path/query sai định dạng, ví dụ id không phải UUID (400). */
    @ExceptionHandler(value = MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handlingTypeMismatch(MethodArgumentTypeMismatchException exception) {
        ApiResponse<Void> apiResponse = ApiResponse.error(
                HttpStatus.BAD_REQUEST.value(),
                "ERR_VALIDATION",
                "Invalid value '" + exception.getValue() + "' for parameter '" + exception.getName() + "'"
        );
        return ResponseEntity.badRequest().body(apiResponse);
    }

    /** Xử lý JSON body sai định dạng hoặc thiếu body (400). */
    @ExceptionHandler(value = HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handlingUnreadableBody(HttpMessageNotReadableException exception) {
        ApiResponse<Void> apiResponse = ApiResponse.error(
                HttpStatus.BAD_REQUEST.value(),
                "ERR_VALIDATION",
                "Request body is missing or malformed"
        );
        return ResponseEntity.badRequest().body(apiResponse);
    }

    private <T> ApiResponse<T> validationResponse(T errors) {
        return new ApiResponse<>(
                HttpStatus.BAD_REQUEST.value(),
                "ERR_VALIDATION",
                "Validation error",
                errors,
                Instant.now()
        );
    }
}
