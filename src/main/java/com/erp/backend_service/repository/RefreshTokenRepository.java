package com.erp.backend_service.repository;

import com.erp.core.domain.RefreshToken;
import com.erp.core.enums.EntityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Truy vấn dữ liệu refresh token (đa hình theo principal). */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Tìm bản ghi refresh token đang active theo mã băm. */
    Optional<RefreshToken> findByTokenHashAndStatus(String tokenHash, EntityStatus status);

    /** Thu hồi (đánh dấu đã thu hồi) mọi refresh token active của một principal. */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.status = :revoked, r.revokedAt = CURRENT_TIMESTAMP " +
            "WHERE r.principalType = :type AND r.principalId = :principalId AND r.status = :active")
    int revokeAll(@Param("type") com.erp.core.enums.PrincipalType type,
                  @Param("principalId") UUID principalId,
                  @Param("active") EntityStatus active,
                  @Param("revoked") EntityStatus revoked);
}
