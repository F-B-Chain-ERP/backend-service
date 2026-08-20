package com.erp.backend_service.mapper;

import com.erp.backend_service.security.CustomUserDetails;
import com.erp.core.domain.Account;
import com.erp.core.dto.auth.AuthResponse;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {
    private static final String TOKEN_TYPE = "Bearer";

    private final AccountMapper accountMapper;

    public AuthMapper(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    public AuthResponse toResponse(String accessToken,
                                   String refreshToken,
                                   long expiresIn,
                                   CustomUserDetails details,
                                   Account account) {
        return new AuthResponse(
                accessToken,
                refreshToken,
                TOKEN_TYPE,
                expiresIn,
                accountMapper.toResponse(account),
                details.getRoles(),
                details.getPermissions(),
                details.getScopes(),
                details.getScopes().isEmpty()
        );
    }
}
