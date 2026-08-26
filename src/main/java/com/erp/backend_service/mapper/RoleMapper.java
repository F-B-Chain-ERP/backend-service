package com.erp.backend_service.mapper;

import com.erp.core.domain.Account;
import com.erp.core.domain.Role;
import com.erp.core.dto.auth.RoleMemberResponse;
import com.erp.core.dto.auth.RoleResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Ánh xạ entity thuộc module role sang các DTO phản hồi tương ứng.
 */
@Component
public class RoleMapper {

    /** Ánh xạ entity Role sang RoleResponse. */
    public RoleResponse toResponse(Role role) {
        return new RoleResponse(
                role.getId().toString(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getRoleType(),
                role.getStatus()
        );
    }

    /** Ánh xạ tài khoản thành viên của một vai trò sang RoleMemberResponse. */
    public RoleMemberResponse toMemberResponse(Account account, Instant assignedAt) {
        return new RoleMemberResponse(
                account.getId(),
                account.getUsername(),
                account.getFullName(),
                account.getEmail(),
                null, // Account chưa có trường department
                assignedAt
        );
    }
}
