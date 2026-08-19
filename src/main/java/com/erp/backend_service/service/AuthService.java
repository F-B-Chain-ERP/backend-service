package com.erp.backend_service.service;

import com.erp.core.dto.auth.AuthResponse;
import com.erp.core.dto.auth.LoginRequest;
import com.erp.core.dto.auth.RefreshTokenRequest;

public interface AuthService {
    
    AuthResponse login(LoginRequest request);
    
    AuthResponse refreshToken(RefreshTokenRequest request);
    
    void logout(String accessToken);
}
