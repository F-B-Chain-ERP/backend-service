package com.erp.backend_service.exception;

import com.erp.core.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<ApiResponse<Void>> handlingRuntimeException(Exception exception) {
        ApiResponse<Void> apiResponse = ApiResponse.error(
                ErrorCode.UNCATEGORIZED_EXCEPTION.getStatusCode(),
                ErrorCode.UNCATEGORIZED_EXCEPTION.getCode(),
                ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(apiResponse);
    }

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

    @ExceptionHandler(value = BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handlingBadCredentials(BadCredentialsException exception) {
        ApiResponse<Void> apiResponse = ApiResponse.error(
                ErrorCode.BAD_CREDENTIALS.getStatusCode(),
                ErrorCode.BAD_CREDENTIALS.getCode(),
                ErrorCode.BAD_CREDENTIALS.getMessage()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(apiResponse);
    }

    @ExceptionHandler(value = {
            DisabledException.class,
            LockedException.class
    })
    public ResponseEntity<ApiResponse<Void>> handlingAccountDisabled(Exception exception) {
        ApiResponse<Void> apiResponse = ApiResponse.error(
                ErrorCode.ACCOUNT_DISABLED.getStatusCode(),
                ErrorCode.ACCOUNT_DISABLED.getCode(),
                ErrorCode.ACCOUNT_DISABLED.getMessage()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(apiResponse);
    }

    @ExceptionHandler(value = AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handlingAuthenticationException(AuthenticationException exception) {
        ApiResponse<Void> apiResponse = ApiResponse.error(
                ErrorCode.UNAUTHENTICATED.getStatusCode(),
                ErrorCode.UNAUTHENTICATED.getCode(),
                exception.getMessage() != null ? exception.getMessage() : ErrorCode.UNAUTHENTICATED.getMessage()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(apiResponse);
    }

    @ExceptionHandler(value = AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handlingAccessDeniedException(AccessDeniedException exception) {
        ApiResponse<Void> apiResponse = ApiResponse.error(
                ErrorCode.UNAUTHORIZED.getStatusCode(),
                ErrorCode.UNAUTHORIZED.getCode(),
                ErrorCode.UNAUTHORIZED.getMessage()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(apiResponse);
    }

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
}
