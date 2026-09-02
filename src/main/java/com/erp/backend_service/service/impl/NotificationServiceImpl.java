package com.erp.backend_service.service.impl;

import com.erp.backend_service.repository.NotificationRepository;
import com.erp.backend_service.service.NotificationService;
import com.erp.core.domain.Notification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Thực hiện tạo thông báo (notification) khi có sự kiện trong hệ thống. */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final String CHANNEL_IN_APP = "IN_APP";
    private static final String STATUS_PENDING = "PENDING";

    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional
    public void notifyAccount(UUID accountId, String title, String body) {
        if (accountId == null || title == null || title.isBlank()) {
            return;
        }
        Notification notification = new Notification();
        notification.setRecipientType("ACCOUNT");
        notification.setAccountId(accountId);
        notification.setChannel(CHANNEL_IN_APP);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setStatus(STATUS_PENDING);
        notificationRepository.save(notification);
    }
}
