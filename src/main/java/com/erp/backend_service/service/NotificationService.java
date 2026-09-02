package com.erp.backend_service.service;

import java.util.UUID;

/** Cung cấp nghiệp vụ tạo thông báo (notification) cho các sự kiện trong hệ thống. */
public interface NotificationService {

    /** Tạo thông báo in-app gửi tới một tài khoản (accountId) với tiêu đề & nội dung cho trước. */
    void notifyAccount(UUID accountId, String title, String body);
}
