package com.erp.backend_service.controller;

import com.erp.backend_service.service.AuthService;
import com.erp.core.dto.auth.AuthResponse;
import com.erp.core.dto.auth.LoginRequest;
import com.erp.core.dto.auth.RefreshTokenRequest;
import com.erp.core.dto.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller xử lý các API xác thực: đăng nhập, làm mới token và đăng xuất.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Đăng nhập bằng username/email và mật khẩu, trả về cặp token. */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** Cấp cặp token mới từ refresh token hợp lệ. */
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** Đăng xuất: kiểm tra access token trong header Authorization rồi trả về thành công. */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        if (token != null) {
            String accessToken;
            if (token.startsWith("Bearer ")) {
                accessToken = token.substring(7);
            } else {
                accessToken = token;
            }
            authService.logout(accessToken);
        }
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }
}
