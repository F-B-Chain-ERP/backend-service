package com.erp.backend_service.mapper;

import com.erp.core.domain.Account;
import com.erp.core.dto.auth.AuthResponse;
import org.springframework.stereotype.Component;

/**
 * Chuyển đổi thông tin xác thực thành {@link AuthResponse} tối giản.
 */
@Component
public class AuthMapper {
    private static final String TOKEN_TYPE = "Bearer";

    private final AccountMapper accountMapper;

    public AuthMapper(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    /** Tạo response xác thực từ token và thông tin tài khoản. */
    public AuthResponse toResponse(String accessToken,
                                    String refreshToken,
                                    boolean requiresScopeAssignment,
                                    Account account) {
        return new AuthResponse(
                accessToken,
                refreshToken,
                TOKEN_TYPE,
                accountMapper.toResponse(account),
                requiresScopeAssignment
        );
    }
}
