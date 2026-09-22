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
import java.util.ArrayList;
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

        // Đẩy thông báo qua Redis Pub/Sub để realtime SSE.
        // Lỗi mạng chỉ warn gọn (không stack): dòng DB đã lưu, chuông đọc lại được khi poll.
        try {
            NotificationResponse response = toResponse(saved);
            String payload = objectMapper.writeValueAsString(response);
            stringRedisTemplate.convertAndSend(RedisKeys.notificationChannel(principalId), payload);
        } catch (Exception e) {
            log.warn("Bỏ qua push Redis cho principal {}: {}", principalId, e.getMessage());
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
        return notificationRepository.countUnreadByPrincipalId(accountId);
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

    /**
     * Fan-out hàng loạt trong 1 transaction (1 round-trip INSERT) cho realtime đơn hàng.
     * Redis publish sau save: lỗi mạng chỉ warn gọn (không kèm stack) vì dòng DB đã lưu,
     * chuông vẫn đọc được khi poll lại — tránh flood log khi Redis chập chờn.
     */
    @Override
    @Transactional
    public List<NotificationResponse> notifyMany(java.util.Set<UUID> accountIds, UUID customerId,
                                                 String title, String body) {
        if ((accountIds == null || accountIds.isEmpty()) && customerId == null) {
            return List.of();
        }
        if (title == null || title.isBlank()) {
            return List.of();
        }
        Instant now = Instant.now();
        List<Notification> entities = new ArrayList<>();
        if (accountIds != null) {
            for (UUID accountId : accountIds) {
                if (accountId == null) {
                    continue;
                }
                entities.add(newNotification(accountId, null, "ACCOUNT", title, body, now));
            }
        }
        if (customerId != null) {
            entities.add(newNotification(null, customerId, "CUSTOMER", title, body, now));
        }
        if (entities.isEmpty()) {
            return List.of();
        }
        List<Notification> saved = notificationRepository.saveAll(entities);
        List<NotificationResponse> responses = new ArrayList<>(saved.size());
        for (Notification notification : saved) {
            responses.add(toResponse(notification));
            try {
                UUID recipientId = notification.getAccountId() != null
                    ? notification.getAccountId() : notification.getCustomerId();
                String payload = objectMapper.writeValueAsString(toResponse(notification));
                stringRedisTemplate.convertAndSend(RedisKeys.notificationChannel(recipientId), payload);
            } catch (Exception e) {
                log.warn("Bỏ qua push Redis cho notification {}: {}", notification.getId(), e.getMessage());
            }
        }
        return responses;
    }

    /**
     * Dọn thông báo đã đọc quá 30 ngày (3h sáng hằng ngày) để bảng không phình
     * vô hạn theo mỗi sự kiện đơn hàng.
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeReadNotifications() {
        try {
            long deleted = notificationRepository.deleteByStatusAndReadAtBefore(
                "READ", Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS));
            if (deleted > 0) {
                log.info("Đã dọn {} thông báo đã đọc quá 30 ngày", deleted);
            }
        } catch (Exception e) {
            log.warn("Dọn thông báo đã đọc thất bại: {}", e.getMessage());
        }
    }

    private Notification newNotification(UUID accountId, UUID customerId, String recipientType,
                                         String title, String body, Instant now) {
        Notification notification = new Notification();
        notification.setRecipientType(recipientType);
        if ("CUSTOMER".equals(recipientType)) {
            notification.setCustomerId(customerId);
        } else {
            notification.setAccountId(accountId);
        }
        notification.setChannel(CHANNEL_IN_APP);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setStatus(STATUS_PENDING);
        notification.setSentAt(now);
        return notification;
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
