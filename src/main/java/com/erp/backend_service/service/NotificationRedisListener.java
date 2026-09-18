package com.erp.backend_service.service;

import com.erp.backend_service.util.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Central Redis Pub/Sub listener: lắng nghe pattern "notification:*" và điều hướng
 * thông báo tới SseEmitter tương ứng của tài khoản cá nhân hoặc nhóm nhân viên chi nhánh trên node này.
 */
@Component
public class NotificationRedisListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationRedisListener.class);

    private final SseEmitterRegistry sseEmitterRegistry;

    public NotificationRedisListener(SseEmitterRegistry sseEmitterRegistry) {
        this.sseEmitterRegistry = sseEmitterRegistry;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            String eventName = payload.contains("\"eventType\"") ? "order_event" : "notification";

            if (channel.startsWith(RedisKeys.BRANCH_NOTIFICATION_CHANNEL_PREFIX)) {
                String branchIdStr = channel.substring(RedisKeys.BRANCH_NOTIFICATION_CHANNEL_PREFIX.length());
                UUID branchId = UUID.fromString(branchIdStr);
                log.debug("Nhận event từ Redis channel {} cho branch {}", channel, branchId);
                sseEmitterRegistry.pushToBranch(branchId, eventName, payload);
            } else if (channel.startsWith(RedisKeys.NOTIFICATION_CHANNEL_PREFIX)) {
                String accountIdStr = channel.substring(RedisKeys.NOTIFICATION_CHANNEL_PREFIX.length());
                UUID accountId = UUID.fromString(accountIdStr);
                log.debug("Nhận event từ Redis channel {} cho account {}", channel, accountId);
                sseEmitterRegistry.push(accountId, eventName, payload);
            }
        } catch (Exception e) {
            log.error("Lỗi khi xử lý message từ Redis Pub/Sub: {}", e.getMessage(), e);
        }
    }
}
