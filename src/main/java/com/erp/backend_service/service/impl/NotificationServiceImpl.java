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

/**
 * Thực hiện tạo và quản lý thông báo (notification), đẩy qua Redis Pub/Sub khi có sự kiện.
 */
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
        notifyPrincipal(accountId, "ACCOUNT", title, body);
    }

    @Override
    @Transactional
    public void notifyCustomer(UUID customerId, String title, String body) {
        notifyPrincipal(customerId, "CUSTOMER", title, body);
    }

    private void notifyPrincipal(UUID principalId, String recipientType, String title, String body) {
        if (principalId == null || title == null || title.isBlank()) {
            return;
        }
        log.info("Notify {}: principalId={}, title={}", recipientType, principalId, title);
        Notification notification = new Notification();
        notification.setRecipientType(recipientType);
        if ("CUSTOMER".equals(recipientType)) {
            notification.setCustomerId(principalId);
        } else {
            notification.setAccountId(principalId);
        }
        notification.setChannel(CHANNEL_IN_APP);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setStatus(STATUS_PENDING);
        notification.setSentAt(Instant.now());
        Notification saved = notificationRepository.save(notification);
        log.info("Notification saved: id={}, principalId={}, title={}", saved.getId(), principalId, title);

        // Đẩy thông báo qua Redis Pub/Sub để realtime SSE
        try {
            NotificationResponse response = toResponse(saved);
            String payload = objectMapper.writeValueAsString(response);
            stringRedisTemplate.convertAndSend(RedisKeys.notificationChannel(principalId), payload);
        } catch (Exception e) {
            log.error("Không thể gửi thông báo realtime qua Redis cho principal: {}", principalId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotifications(UUID accountId) {
        if (accountId == null) {
            return List.of();
        }
        log.info("Get unread notifications: accountId={}", accountId);
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
        int pageSize = limit > 0 ? limit : 20;
        return notificationRepository.findRecentByPrincipalId(accountId, org.springframework.data.domain.PageRequest.of(0, pageSize))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID accountId) {
        if (accountId == null) {
            return 0L;
        }
        log.info("Get unread count: accountId={}", accountId);
        return notificationRepository.countUnreadByPrincipalId(accountId);
    }

    @Override
    @Transactional
    public void markAsRead(UUID id, UUID accountId) {
        if (id == null || accountId == null) {
            return;
        }
        log.info("Mark notification read: id={}, accountId={}", id, accountId);
        notificationRepository.markAsRead(id, accountId, Instant.now());
    }

    @Override
    @Transactional
    public void markAllAsRead(UUID accountId) {
        if (accountId == null) {
            return;
        }
        log.info("Mark all notifications read: accountId={}", accountId);
        notificationRepository.markAllAsRead(accountId, Instant.now());
    }

    @Override
    @Transactional
    public void deleteNotification(UUID id, UUID accountId) {
        if (id == null || accountId == null) {
            return;
        }
        log.info("Delete notification id={}, accountId={}", id, accountId);
        notificationRepository.deleteByIdAndAccountId(id, accountId);
    }

    @Override
    @Transactional
    public void deleteAllNotifications(UUID accountId) {
        if (accountId == null) {
            return;
        }
        log.info("Delete all notifications: accountId={}", accountId);
        notificationRepository.deleteAllByAccountId(accountId);
    }

    @Override
    @Transactional
    public void deleteReadNotifications(UUID accountId) {
        if (accountId == null) {
            return;
        }
        log.info("Delete read notifications: accountId={}", accountId);
        notificationRepository.deleteReadByAccountId(accountId);
    }

    private NotificationResponse toResponse(Notification n) {
        String actionUrl = resolveActionUrl(n.getTitle(), n.getBody(), n.getRecipientType());
        String type = resolveNotificationType(n.getTitle(), n.getBody());
        UUID recipientId = n.getAccountId() != null ? n.getAccountId() : n.getCustomerId();
        return new NotificationResponse(
                n.getId(),
                recipientId,
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
        if (text.contains("đơn hàng mới") || text.contains("đặt hàng thành công")) {
            return "ORDER_CREATED";
        }
        if (text.contains("confirmed") || text.contains("đã xác nhận")) {
            return "ORDER_CONFIRMED";
        }
        if (text.contains("preparing") || text.contains("đang thực hiện") || text.contains("đang pha chế")) {
            return "ORDER_PREPARING";
        }
        if (text.contains("ready") || text.contains("đã chuẩn bị xong") || text.contains("chờ lấy")) {
            return "ORDER_READY";
        }
        if (text.contains("delivering") || text.contains("đang giao")) {
            return "ORDER_DELIVERING";
        }
        if (text.contains("completed") || text.contains("hoàn tất") || text.contains("thành công")) {
            return "ORDER_COMPLETED";
        }
        if (text.contains("chờ duyệt") || text.contains("trình duyệt")) {
            return "PO_SUBMITTED";
        }
        if (text.contains("đã được duyệt") || text.contains("phê duyệt") || text.contains("đã duyệt")) {
            return "PO_APPROVED";
        }
        if (text.contains("từ chối") || text.contains("bị từ chối")) {
            return "REJECTED";
        }
        if (text.contains("bị huỷ") || text.contains("đã bị hủy") || text.contains("hủy đơn")) {
            return text.contains("hd-") ? "ORDER_CANCELLED" : "PO_CANCELLED";
        }
        if (text.contains("nhập kho")) {
            return "PO_RECEIVED";
        }
        return "GENERAL";
    }

    private String resolveActionUrl(String title, String body, String recipientType) {
        String content = (title != null ? title : "") + " " + (body != null ? body : "");

        // 1. Nhận diện mã đơn hàng POS: HD-YYYYMMDD-XXXX
        java.util.regex.Matcher hdMatcher = java.util.regex.Pattern.compile("HD-[\\w-]+").matcher(content);
        if (hdMatcher.find()) {
            String orderCode = hdMatcher.group();
            if ("CUSTOMER".equalsIgnoreCase(recipientType)) {
                return "/store/orders?orderCode=" + orderCode;
            } else {
                return "/admin/pos/orders/list?code=" + orderCode;
            }
        }

        // 2. Nhận diện mã đơn mua hàng: PO-...
        java.util.regex.Matcher poMatcher = java.util.regex.Pattern.compile("PO-[\\w-]+").matcher(content);
        if (poMatcher.find()) {
            return "/admin/procurement/purchase-orders/list?code=" + poMatcher.group();
        }

        return null;
    }
}
