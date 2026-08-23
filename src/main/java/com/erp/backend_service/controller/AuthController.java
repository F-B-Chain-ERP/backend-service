package com.erp.backend_service.controller;

import com.erp.backend_service.service.AuthService;
import com.erp.core.dto.auth.AuthResponse;
import com.erp.core.dto.auth.ChangePasswordRequest;
import com.erp.core.dto.auth.ForgotPasswordRequest;
import com.erp.core.dto.auth.GoogleOAuth2Request;
import com.erp.core.dto.auth.LoginRequest;
import com.erp.core.dto.auth.RefreshTokenRequest;
import com.erp.core.dto.auth.RegisterCustomerRequest;
import com.erp.core.dto.auth.ResendOtpRequest;
import com.erp.core.dto.auth.ResetPasswordOtpRequest;
import com.erp.core.dto.auth.SelectBranchRequest;
import com.erp.core.dto.auth.VerifyOtpRequest;
import com.erp.core.dto.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Controller xử lý các API xác thực: đăng ký khách hàng, đăng nhập,
 * làm mới token và đăng xuất (cho cả tài khoản nội bộ và khách hàng).
 */
@RestController
@RequestMapping("/api/v1/auth")
@Validated
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Đăng ký tài khoản khách hàng (chỉ dành cho customer). */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterCustomerRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.registerCustomer(request)));
    }

    /** Đăng nhập bằng username/email/phone và mật khẩu, trả về cặp token. */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }

    /** Xác thực mã OTP email của phiên đăng ký / đăng nhập, trả về cặp token. */
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmail(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.verifyEmailOtp(request)));
    }

    /** Gửi lại mã OTP xác thực email cho phiên đăng ký hiện tại. */
    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.resendRegistrationOtp(request)));
    }

    /** Quên mật khẩu: gửi mã OTP qua email và trả về reset token. */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<AuthResponse>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.forgotPassword(request)));
    }

    /** Đặt lại mật khẩu bằng mã OTP và reset token. */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<AuthResponse>> resetPassword(@Valid @RequestBody ResetPasswordOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.resetPassword(request)));
    }

    /** Đổi mật khẩu của người dùng đang đăng nhập (cần mật khẩu cũ). */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<AuthResponse>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.changePassword(request)));
    }

    /** Đăng nhập / đăng ký khách hàng qua Google OAuth2 (gửi Google ID token). */
    @PostMapping("/oauth2/google")
    public ResponseEntity<ApiResponse<AuthResponse>> googleOAuth2(@Valid @RequestBody GoogleOAuth2Request request) {
        return ResponseEntity.ok(ApiResponse.success(authService.authenticateWithGoogle(request)));
    }

    /** Cấp cặp token mới từ refresh token hợp lệ. */
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.refreshToken(request)));
    }

    /** Chọn đơn vị (chi nhánh) làm việc sau khi đăng nhập, trả về token chứa branchId. */
    @PostMapping("/select-branch")
    public ResponseEntity<ApiResponse<AuthResponse>> selectBranch(@Valid @RequestBody SelectBranchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.selectBranch(request)));
    }

    /** Đăng xuất: thu hồi access token và refresh token tương ứng. */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody RefreshTokenRequest refreshTokenRequest
    ) {
        String accessToken = authorization != null ? authorization : "";
        authService.logout(accessToken, refreshTokenRequest.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }
}
