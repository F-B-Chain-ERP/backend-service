package com.erp.backend_service.service.pos;

import com.erp.backend_service.event.OrderRealtimeEvent;
import com.erp.backend_service.repository.NotificationRepository;
import com.erp.backend_service.service.NotificationResolverService;
import com.erp.backend_service.util.RedisKeys;
import com.erp.core.domain.Notification;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Lắng nghe và phát sự kiện thời gian thực cho đơn hàng (POS Order) sau khi Transaction đã commit thành công.
 * Đẩy qua Redis Pub/Sub cho Khách hàng, Chi nhánh (Nhân viên/Admin), và Tài xế (Shipper).
 */
@Service
public class OrderRealtimePublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderRealtimePublisher.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;
    private final NotificationResolverService notificationResolverService;

    public OrderRealtimePublisher(
            StringRedisTemplate stringRedisTemplate,
            NotificationRepository notificationRepository,
            ObjectMapper objectMapper,
            NotificationResolverService notificationResolverService
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
        this.notificationResolverService = notificationResolverService;
    }

    /**
     * Lắng nghe sự kiện sau khi DB transaction đã commit thành công 100%.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderRealtimeEvent(OrderRealtimeEvent event) {
        if (event == null) {
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(event);

            // 1. Broadcast tới toàn bộ nhân viên tại chi nhánh & lưu Notification DB cho managers/admins
            if (event.branchId() != null) {
                String branchChannel = RedisKeys.branchNotificationChannel(event.branchId());
                stringRedisTemplate.convertAndSend(branchChannel, payload);
                log.debug("Đã publish OrderRealtimeEvent tới branch channel {}: {}", branchChannel, event.orderCode());

                Set<UUID> managersAndAdmins = notificationResolverService.resolveManagersAndAdmins(event.branchId(), null);
                if (managersAndAdmins != null) {
                    for (UUID adminOrManagerId : managersAndAdmins) {
                        saveNotificationRecord(adminOrManagerId, "ACCOUNT", event.title(), event.message());
                    }
                }
            } else {
                Set<UUID> admins = notificationResolverService.resolveManagersAndAdmins(null, null);
                if (admins != null) {
                    for (UUID adminId : admins) {
                        saveNotificationRecord(adminId, "ACCOUNT", event.title(), event.message());
                    }
                }
            }

            // 2. Gửi tới Khách hàng (nếu có customerId)
            if (event.customerId() != null) {
                String customerChannel = RedisKeys.notificationChannel(event.customerId());
                stringRedisTemplate.convertAndSend(customerChannel, payload);
                saveNotificationRecord(event.customerId(), "CUSTOMER", event.title(), event.message());
                log.debug("Đã publish OrderRealtimeEvent tới customer {}: {}", event.customerId(), event.orderCode());
            }

            // 3. Gửi tới Tài xế / Shipper (nếu có shipperId)
            if (event.shipperId() != null) {
                String shipperChannel = RedisKeys.notificationChannel(event.shipperId());
                stringRedisTemplate.convertAndSend(shipperChannel, payload);
                saveNotificationRecord(event.shipperId(), "ACCOUNT", event.title(), event.message());
                log.debug("Đã publish OrderRealtimeEvent tới shipper {}: {}", event.shipperId(), event.orderCode());
            }
        } catch (Exception e) {
            log.error("Lỗi khi phát sự kiện realtime cho đơn hàng {}: {}", event.orderCode(), e.getMessage(), e);
        }
    }

    private void saveNotificationRecord(UUID targetId, String recipientType, String title, String body) {
        try {
            if (title == null || title.isBlank() || targetId == null) {
                return;
            }
            Notification notification = new Notification();
            notification.setRecipientType(recipientType);
            if ("CUSTOMER".equalsIgnoreCase(recipientType)) {
                notification.setCustomerId(targetId);
                notification.setAccountId(null);
            } else {
                notification.setAccountId(targetId);
                notification.setCustomerId(null);
            }
            notification.setChannel("IN_APP");
            notification.setTitle(title);
            notification.setBody(body);
            notification.setStatus("PENDING");
            notification.setSentAt(Instant.now());
            notificationRepository.save(notification);
        } catch (Exception e) {
            log.warn("Không thể lưu notification DB cho targetId={}: {}", targetId, e.getMessage());
        }
    }
}
