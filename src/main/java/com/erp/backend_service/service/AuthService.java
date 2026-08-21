package com.erp.backend_service.service;

import com.erp.core.dto.auth.AuthResponse;
import com.erp.core.dto.auth.LoginRequest;
import com.erp.core.dto.auth.RefreshTokenRequest;

/**
 * Cung cấp các nghiệp vụ xác thực: đăng nhập, làm mới token và đăng xuất.
 */
public interface AuthService {

    /**
     * Xác thực thông tin đăng nhập và cấp cặp access/refresh token.
     *
     * @param request tên đăng nhập/email và mật khẩu
     * @return kết quả xác thực gồm token và thông tin tài khoản
     */
    AuthResponse login(LoginRequest request);

    /**
     * Dùng refresh token hợp lệ để cấp cặp token mới.
     *
     * @param request refresh token hiện tại
     * @return kết quả xác thực với token mới
     */
    AuthResponse refreshToken(RefreshTokenRequest request);

    /**
     * Đăng xuất bằng cách kiểm tra hợp lệ của access token.
     *
     * @param accessToken access token (có thể mang tiền tố "Bearer ")
     */
    void logout(String accessToken);
}
