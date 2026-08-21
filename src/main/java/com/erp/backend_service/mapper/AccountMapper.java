package com.erp.backend_service.mapper;

import com.erp.core.domain.Account;
import com.erp.core.dto.auth.AccountResponse;
import org.springframework.stereotype.Component;

/** Chuyển đổi entity Account sang DTO phản hồi AccountResponse. */
@Component
public class AccountMapper {

    /** Ánh xạ thông tin tài khoản sang response (ẩn mật khẩu, chỉ báo có/không). */
    public AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getUsername(),
                account.getEmail(),
                account.getFullName(),
                account.getAuthProvider(),
                account.getPassword() != null,
                account.getStatus(),
                account.getLastLoginAt(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
