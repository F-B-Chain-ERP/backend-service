package com.erp.backend_service.repository;

import com.erp.core.domain.AccountRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.erp.core.enums.EntityStatus;

/** Truy vấn bản ghi gán vai trò cho tài khoản (AccountRole). */
@Repository
public interface AccountRoleRepository extends JpaRepository<AccountRole, UUID> {

    @Query("""
            select a from AccountRole a
            where a.accountId = :accountId
              and a.status = :status
              and (a.expiresAt is null or a.expiresAt > :now)
            """)
    /** Lấy các bản ghi gán vai trò đang active và chưa hết hạn của một tài khoản. */
    List<AccountRole> findEffectiveByAccountId(
            @Param("accountId") UUID accountId,
            @Param("status") EntityStatus status,
            @Param("now") Instant now
    );

    /** Tìm bản ghi gán vai trò theo tài khoản, vai trò và phạm vi (dùng kiểm tra trùng). */
    Optional<AccountRole> findByAccountIdAndRoleIdAndScopeId(
            UUID accountId, UUID roleId, UUID scopeId
    );

    /** Lấy tất cả bản ghi gán vai trò của một tài khoản. */
    List<AccountRole> findByAccountId(UUID accountId);
}
