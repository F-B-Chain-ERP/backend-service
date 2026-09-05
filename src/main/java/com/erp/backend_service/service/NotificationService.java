package com.erp.backend_service.service;

import com.erp.core.dto.response.notification.NotificationResponse;

import java.util.List;
import java.util.UUID;

/** Cung cấp nghiệp vụ tạo và truy xuất thông báo (notification) cho các sự kiện trong hệ thống. */
public interface NotificationService {

    /** Tạo thông báo in-app gửi tới một tài khoản (accountId) với tiêu đề & nội dung cho trước. */
    void notifyAccount(UUID accountId, String title, String body);

    /** Lấy danh sách thông báo chưa đọc của tài khoản. */
    List<NotificationResponse> getUnreadNotifications(UUID accountId);

    /** Lấy danh sách thông báo gần đây của tài khoản. */
    List<NotificationResponse> getRecentNotifications(UUID accountId, int limit);

    /** Đếm số lượng thông báo chưa đọc của tài khoản. */
    long getUnreadCount(UUID accountId);

    /** Đánh dấu một thông báo đã đọc. */
    void markAsRead(UUID id, UUID accountId);

    /** Đánh dấu tất cả thông báo của tài khoản là đã đọc. */
    void markAllAsRead(UUID accountId);

    /** Xóa một thông báo theo ID của tài khoản. */
    void deleteNotification(UUID id, UUID accountId);

    /** Xóa tất cả thông báo của tài khoản. */
    void deleteAllNotifications(UUID accountId);

    /** Xóa các thông báo đã đọc của tài khoản. */
    void deleteReadNotifications(UUID accountId);
}
