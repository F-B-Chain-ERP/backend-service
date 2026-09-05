package com.erp.backend_service.repository;

import com.erp.core.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Truy vấn dữ liệu thông báo (notification). */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** Lấy danh sách thông báo chưa đọc của tài khoản, sắp xếp mới nhất trước. */
    List<Notification> findByAccountIdAndReadAtIsNullOrderByCreatedAtDesc(UUID accountId);

    /** Lấy danh sách thông báo gần đây của tài khoản. */
    List<Notification> findTop20ByAccountIdOrderByCreatedAtDesc(UUID accountId);

    /** Đếm số thông báo chưa đọc của tài khoản. */
    long countByAccountIdAndReadAtIsNull(UUID accountId);

    /** Đánh dấu một thông báo là đã đọc. */
    @Modifying
    @Query("update Notification n set n.readAt = :readAt, n.status = 'READ' where n.id = :id and n.accountId = :accountId")
    int markAsRead(@Param("id") UUID id, @Param("accountId") UUID accountId, @Param("readAt") Instant readAt);

    /** Đánh dấu tất cả thông báo chưa đọc của tài khoản là đã đọc. */
    @Modifying
    @Query("update Notification n set n.readAt = :readAt, n.status = 'READ' where n.accountId = :accountId and n.readAt is null")
    int markAllAsRead(@Param("accountId") UUID accountId, @Param("readAt") Instant readAt);

    /** Xóa một thông báo theo id và accountId. */
    @Modifying
    @Query("delete from Notification n where n.id = :id and n.accountId = :accountId")
    int deleteByIdAndAccountId(@Param("id") UUID id, @Param("accountId") UUID accountId);

    /** Xóa tất cả thông báo của tài khoản. */
    @Modifying
    @Query("delete from Notification n where n.accountId = :accountId")
    int deleteAllByAccountId(@Param("accountId") UUID accountId);

    /** Xóa tất cả thông báo đã đọc của tài khoản. */
    @Modifying
    @Query("delete from Notification n where n.accountId = :accountId and n.readAt is not null")
    int deleteReadByAccountId(@Param("accountId") UUID accountId);
}
