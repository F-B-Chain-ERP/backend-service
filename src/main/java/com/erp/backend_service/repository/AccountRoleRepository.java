package com.erp.backend_service.repository;

import com.erp.core.domain.AccountRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
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

    /** Kiểm tra phạm vi còn được gán cho bất kỳ tài khoản nào không (dùng chặn xóa). */
    boolean existsByScopeId(UUID scopeId);

    /**
     * Lấy danh sách tài khoản (không trùng) đang được gán effective một trong các
     * vai trò chỉ định, dùng để làm mới snapshot quyền khi quyền/vai trò thay đổi.
     */
    @Query("""
            select distinct a.accountId from AccountRole a
            where a.roleId in :roleIds
              and a.status = :status
            """)
    List<UUID> findAccountIdsByRoleIdIn(
            @Param("roleIds") Collection<UUID> roleIds,
            @Param("status") EntityStatus status
    );

    /**
     * Lấy các bản ghi gán vai trò đang effective (active và chưa hết hạn) của một
     * vai trò, dùng để liệt kê thành viên của vai trò.
     */
    @Query("""
            select a from AccountRole a
            where a.roleId = :roleId
              and a.status = :status
              and (a.expiresAt is null or a.expiresAt > :now)
            """)
    List<AccountRole> findEffectiveByRoleId(
            @Param("roleId") UUID roleId,
            @Param("status") EntityStatus status,
            @Param("now") Instant now
    );

    /**
     * Lấy danh sách accountId được gán một trong các vai trò chỉ định tại một phạm vi cụ thể (ví dụ chi nhánh).
     */
    @Query("""
            select distinct a.accountId from AccountRole a
            where a.roleId in :roleIds
              and a.scopeId = :scopeId
              and a.status = :status
              and (a.expiresAt is null or a.expiresAt > :now)
            """)
    List<UUID> findAccountIdsByRoleIdInAndScopeId(
            @Param("roleIds") Collection<UUID> roleIds,
            @Param("scopeId") UUID scopeId,
            @Param("status") EntityStatus status,
            @Param("now") Instant now
    );

    /**
     * Lấy danh sách accountId được gán vai trò có phạm vi ALL_SYSTEM (quản trị toàn hệ thống).
     */
    @Query("""
            select distinct a.accountId from AccountRole a, Scope s
            where a.scopeId = s.id
              and s.scopeType = com.erp.core.enums.ScopeType.ALL_SYSTEM
              and s.status = :status
              and a.status = :status
              and (a.expiresAt is null or a.expiresAt > :now)
            """)
    List<UUID> findAccountIdsByAllSystemScope(
            @Param("status") EntityStatus status,
            @Param("now") Instant now
    );

    /**
     * Lấy danh sách accountId được gán một trong các vai trò chỉ định tại các scope thuộc về chi nhánh.
     */
    @Query("""
            select distinct a.accountId from AccountRole a, Scope s
            where a.scopeId = s.id
              and a.roleId in :roleIds
              and s.branchId = :branchId
              and s.status = :status
              and a.status = :status
              and (a.expiresAt is null or a.expiresAt > :now)
            """)
    List<UUID> findAccountIdsByRoleIdInAndBranchId(
            @Param("roleIds") Collection<UUID> roleIds,
            @Param("branchId") UUID branchId,
            @Param("status") EntityStatus status,
            @Param("now") Instant now
    );
}
