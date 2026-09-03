package com.erp.backend_service.mapper;

import com.erp.core.domain.Account;
import com.erp.core.dto.auth.AccountResponse;
import com.erp.core.dto.auth.AccountBranchResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/** Chuyển đổi entity Account sang DTO phản hồi AccountResponse. */
@Component
public class AccountMapper {

    /** Ánh xạ thông tin tài khoản sang response (ẩn mật khẩu, chỉ báo có/không). */
    public AccountResponse toResponse(Account account, List<AccountBranchResponse> branches) {
        return new AccountResponse(
                account.getId(),
                account.getUsername(),
                account.getEmail(),
                account.getFullName(),
                account.getPhone(),
                account.getAvatarUrl(),
                account.getAuthProvider(),
                account.isHasLocalPassword(),
                account.getStatus(),
                account.getPrimaryBranchId(),
                branches != null ? branches : List.of(),
                account.getLastLoginAt(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
