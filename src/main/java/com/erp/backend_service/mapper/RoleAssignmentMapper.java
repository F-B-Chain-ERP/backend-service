package com.erp.backend_service.mapper;

import com.erp.core.domain.AccountRole;
import com.erp.core.domain.Scope;
import com.erp.core.dto.auth.RoleAssignmentResponse;
import com.erp.core.dto.auth.ScopeResponse;
import org.springframework.stereotype.Component;

/** Chuyển đổi giữa entity AccountRole/Scope và DTO phản hồi RoleAssignmentResponse. */
@Component
public class RoleAssignmentMapper {

    /** Ánh xạ một bản ghi gán vai trò và phạm vi tương ứng sang response. */
    public RoleAssignmentResponse toResponse(AccountRole assignment, Scope scope) {
        return new RoleAssignmentResponse(
                assignment.getId(),
                assignment.getAccountId(),
                assignment.getRoleId(),
                new ScopeResponse(scope.getId(), scope.getScopeType(), scope.getBranchId()),
                assignment.getStatus(),
                assignment.getAssignedAt(),
                assignment.getExpiresAt()
        );
    }
}
