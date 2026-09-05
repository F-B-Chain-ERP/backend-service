package com.erp.backend_service.service.impl;

import com.erp.backend_service.repository.NotificationRepository;
import com.erp.backend_service.service.NotificationService;
import com.erp.backend_service.util.RedisKeys;
import com.erp.core.domain.Notification;
import com.erp.core.dto.response.notification.NotificationResponse;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Thực hiện tạo và quản lý thông báo (notification), đẩy qua Redis Pub/Sub khi có sự kiện. */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);
    private static final String CHANNEL_IN_APP = "IN_APP";
    private static final String STATUS_PENDING = "PENDING";

    private final NotificationRepository notificationRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper
    ) {
        this.notificationRepository = notificationRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
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
        notification.setSentAt(Instant.now());
        Notification saved = notificationRepository.save(notification);

        // Đẩy thông báo qua Redis Pub/Sub để realtime SSE
        try {
            NotificationResponse response = toResponse(saved);
            String payload = objectMapper.writeValueAsString(response);
            stringRedisTemplate.convertAndSend(RedisKeys.notificationChannel(accountId), payload);
        } catch (Exception e) {
            log.error("Không thể gửi thông báo realtime qua Redis cho account: {}", accountId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotifications(UUID accountId) {
        if (accountId == null) {
            return List.of();
        }
        return notificationRepository.findByAccountIdAndReadAtIsNullOrderByCreatedAtDesc(accountId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getRecentNotifications(UUID accountId, int limit) {
        if (accountId == null) {
            return List.of();
        }
        return notificationRepository.findTop20ByAccountIdOrderByCreatedAtDesc(accountId)
                .stream()
                .limit(limit > 0 ? limit : 20)
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID accountId) {
        if (accountId == null) {
            return 0L;
        }
        return notificationRepository.countByAccountIdAndReadAtIsNull(accountId);
    }

    @Override
    @Transactional
    public void markAsRead(UUID id, UUID accountId) {
        if (id == null || accountId == null) {
            return;
        }
        notificationRepository.markAsRead(id, accountId, Instant.now());
    }

    @Override
    @Transactional
    public void markAllAsRead(UUID accountId) {
        if (accountId == null) {
            return;
        }
        notificationRepository.markAllAsRead(accountId, Instant.now());
    }

    @Override
    @Transactional
    public void deleteNotification(UUID id, UUID accountId) {
        if (id == null || accountId == null) {
            return;
        }
        notificationRepository.deleteByIdAndAccountId(id, accountId);
    }

    @Override
    @Transactional
    public void deleteAllNotifications(UUID accountId) {
        if (accountId == null) {
            return;
        }
        notificationRepository.deleteAllByAccountId(accountId);
    }

    @Override
    @Transactional
    public void deleteReadNotifications(UUID accountId) {
        if (accountId == null) {
            return;
        }
        notificationRepository.deleteReadByAccountId(accountId);
    }

    private NotificationResponse toResponse(Notification n) {
        String actionUrl = resolveActionUrl(n.getTitle(), n.getBody());
        String type = resolveNotificationType(n.getTitle(), n.getBody());
        return new NotificationResponse(
                n.getId(),
                n.getAccountId(),
                n.getTitle(),
                n.getBody(),
                n.getStatus(),
                n.getSentAt(),
                n.getReadAt(),
                n.getCreatedAt(),
                actionUrl,
                type
        );
    }

    private String resolveNotificationType(String title, String body) {
        String text = ((title != null ? title : "") + " " + (body != null ? body : "")).toLowerCase();
        if (text.contains("chờ duyệt") || text.contains("trình duyệt")) {
            return "PO_SUBMITTED";
        }
        if (text.contains("đã được duyệt") || text.contains("phê duyệt") || text.contains("đã duyệt")) {
            return "PO_APPROVED";
        }
        if (text.contains("từ chối") || text.contains("bị từ chối")) {
            return "PO_REJECTED";
        }
        if (text.contains("bị huỷ") || text.contains("đã bị hủy") || text.contains("hủy đơn")) {
            return "PO_CANCELLED";
        }
        if (text.contains("nhập kho")) {
            return "PO_RECEIVED";
        }
        return "GENERAL";
    }

    private String resolveActionUrl(String title, String body) {
        if (body != null) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("PO-[\\w-]+").matcher(body);
            if (matcher.find()) {
                return "/admin/procurement/purchase-orders/list?code=" + matcher.group();
            }
        }
        if (title != null) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("PO-[\\w-]+").matcher(title);
            if (matcher.find()) {
                return "/admin/procurement/purchase-orders/list?code=" + matcher.group();
            }
        }
        return null;
    }
}
