package com.erp.backend_service.service.pos;

import com.erp.backend_service.event.OrderRealtimeEvent;
import com.erp.backend_service.service.NotificationResolverService;
import com.erp.backend_service.service.NotificationService;
import com.erp.backend_service.util.RedisKeys;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

/**
 * Lắng nghe và phát sự kiện thời gian thực cho đơn hàng (POS Order) sau khi Transaction đã commit thành công.
 * Đẩy qua Redis Pub/Sub cho Khách hàng, Chi nhánh (Nhân viên/Admin), và Tài xế (Shipper).
 */
@Service
public class OrderRealtimePublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderRealtimePublisher.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final NotificationResolverService notificationResolverService;

    public OrderRealtimePublisher(StringRedisTemplate stringRedisTemplate, NotificationService notificationService, ObjectMapper objectMapper, NotificationResolverService notificationResolverService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.notificationService = notificationService;
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

            // Lưu và phát notification có ID thật trước, để chuông có thể đọc/xóa ngay khi nhận SSE.
            Set<UUID> accountRecipients;
            if (event.branchId() != null) {
                accountRecipients = new HashSet<>(notificationResolverService.resolveBranchStaffAndAdmins(event.branchId(), null));
            } else {
                accountRecipients = new HashSet<>(notificationResolverService.resolveManagersAndAdmins(null, null));
            }
            if (event.shipperId() != null) {
                accountRecipients.add(event.shipperId());
            }
            for (UUID accountId : accountRecipients) {
                try {
                    notificationService.notifyAccount(accountId, event.title(), event.message());
                } catch (Exception e) {
                    log.warn("Không thể lưu notification đơn hàng cho account {}: {}", accountId, e.getMessage());
                }
            }

            if (event.customerId() != null) {
                try {
                    notificationService.notifyCustomer(event.customerId(), event.title(), event.message());
                } catch (Exception e) {
                    log.warn("Không thể lưu notification đơn hàng cho customer {}: {}", event.customerId(), e.getMessage());
                }
            }

            // Broadcast event nghiệp vụ sau notification để các màn hình cập nhật trạng thái.
            if (event.branchId() != null) {
                String branchChannel = RedisKeys.branchNotificationChannel(event.branchId());
                stringRedisTemplate.convertAndSend(branchChannel, payload);
                log.debug("Đã publish OrderRealtimeEvent tới branch channel {}: {}", branchChannel, event.orderCode());
            }
            if (event.customerId() != null) {
                stringRedisTemplate.convertAndSend(RedisKeys.notificationChannel(event.customerId()), payload);
                log.debug("Đã publish OrderRealtimeEvent tới customer {}: {}", event.customerId(), event.orderCode());
            }
            if (event.shipperId() != null) {
                stringRedisTemplate.convertAndSend(RedisKeys.notificationChannel(event.shipperId()), payload);
                log.debug("Đã publish OrderRealtimeEvent tới shipper {}: {}", event.shipperId(), event.orderCode());
            }
        } catch (Exception e) {
            log.error("Lỗi khi phát sự kiện realtime cho đơn hàng {}: {}", event.orderCode(), e.getMessage(), e);
        }
    }

}
