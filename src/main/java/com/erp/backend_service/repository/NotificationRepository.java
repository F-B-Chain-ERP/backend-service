package com.erp.backend_service.repository;

import com.erp.core.domain.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Truy vấn dữ liệu thông báo (notification) cho cả Account (nhân viên/admin) và Customer (khách hàng). */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** Lấy danh sách thông báo chưa đọc của tài khoản hoặc khách hàng, sắp xếp mới nhất trước. */
    @Query("select n from Notification n where (n.accountId = :principalId or n.customerId = :principalId) and n.readAt is null order by n.createdAt desc")
    List<Notification> findUnreadByPrincipalId(@Param("principalId") UUID principalId);

    /** Lấy danh sách thông báo gần đây của tài khoản hoặc khách hàng. */
    @Query("select n from Notification n where (n.accountId = :principalId or n.customerId = :principalId) order by n.createdAt desc")
    List<Notification> findRecentByPrincipalId(@Param("principalId") UUID principalId, Pageable pageable);

    /** Đếm số thông báo chưa đọc của tài khoản hoặc khách hàng. */
    @Query("select count(n) from Notification n where (n.accountId = :principalId or n.customerId = :principalId) and n.readAt is null")
    long countUnreadByPrincipalId(@Param("principalId") UUID principalId);

    /** Đánh dấu một thông báo là đã đọc. */
    @Modifying
    @Query("update Notification n set n.readAt = :readAt, n.status = 'READ' where n.id = :id and (n.accountId = :principalId or n.customerId = :principalId)")
    int markAsRead(@Param("id") UUID id, @Param("principalId") UUID principalId, @Param("readAt") Instant readAt);

    /** Đánh dấu tất cả thông báo chưa đọc của tài khoản/khách hàng là đã đọc. */
    @Modifying
    @Query("update Notification n set n.readAt = :readAt, n.status = 'READ' where (n.accountId = :principalId or n.customerId = :principalId) and n.readAt is null")
    int markAllAsRead(@Param("principalId") UUID principalId, @Param("readAt") Instant readAt);

    /** Xóa một thông báo theo id và principalId. */
    @Modifying
    @Query("delete from Notification n where n.id = :id and (n.accountId = :principalId or n.customerId = :principalId)")
    int deleteByIdAndAccountId(@Param("id") UUID id, @Param("principalId") UUID principalId);

    /** Xóa tất cả thông báo của tài khoản/khách hàng. */
    @Modifying
    @Query("delete from Notification n where (n.accountId = :principalId or n.customerId = :principalId)")
    int deleteAllByAccountId(@Param("principalId") UUID principalId);

    /** Xóa tất cả thông báo đã đọc của tài khoản/khách hàng. */
    @Modifying
    @Query("delete from Notification n where (n.accountId = :principalId or n.customerId = :principalId) and n.readAt is not null")
    int deleteReadByAccountId(@Param("principalId") UUID principalId);

    // Các alias phương thức cũ để tương thích hoàn toàn
    default List<Notification> findByAccountIdAndReadAtIsNullOrderByCreatedAtDesc(UUID accountId) {
        return findUnreadByPrincipalId(accountId);
    }

    default List<Notification> findTop20ByAccountIdOrderByCreatedAtDesc(UUID accountId) {
        return findRecentByPrincipalId(accountId, org.springframework.data.domain.PageRequest.of(0, 20));
    }

    default long countByAccountIdAndReadAtIsNull(UUID accountId) {
        return countUnreadByPrincipalId(accountId);
    }
}
