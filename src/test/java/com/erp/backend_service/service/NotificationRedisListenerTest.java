package com.erp.backend_service.service;

import com.erp.backend_service.util.RedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationRedisListenerTest {

    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    private NotificationRedisListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationRedisListener(sseEmitterRegistry);
    }

    @Test
    @DisplayName("Nên điều hướng tin nhắn kênh chi nhánh tới pushToBranch với event order_event")
    void shouldRouteBranchChannelToPushToBranch() {
        UUID branchId = UUID.randomUUID();
        String channel = RedisKeys.branchNotificationChannel(branchId);
        String payload = "{\"eventType\":\"ORDER_CREATED\",\"orderId\":\"123\"}";

        DefaultMessage message = new DefaultMessage(
                channel.getBytes(StandardCharsets.UTF_8),
                payload.getBytes(StandardCharsets.UTF_8)
        );

        listener.onMessage(message, null);

        verify(sseEmitterRegistry).pushToBranch(eq(branchId), eq("order_event"), eq(payload));
        verify(sseEmitterRegistry, never()).push(any(), any(), any());
    }

    @Test
    @DisplayName("Nên điều hướng tin nhắn kênh account tới push cá nhân với event order_event")
    void shouldRouteAccountChannelToPush() {
        UUID accountId = UUID.randomUUID();
        String channel = RedisKeys.notificationChannel(accountId);
        String payload = "{\"eventType\":\"ORDER_STATUS_CHANGED\",\"orderId\":\"456\"}";

        DefaultMessage message = new DefaultMessage(
                channel.getBytes(StandardCharsets.UTF_8),
                payload.getBytes(StandardCharsets.UTF_8)
        );

        listener.onMessage(message, null);

        verify(sseEmitterRegistry).push(eq(accountId), eq("order_event"), eq(payload));
        verify(sseEmitterRegistry, never()).pushToBranch(any(), any(), any());
    }

    @Test
    @DisplayName("Nên dùng event notification khi payload không chứa eventType")
    void shouldDefaultToNotificationEvent() {
        UUID accountId = UUID.randomUUID();
        String channel = RedisKeys.notificationChannel(accountId);
        String payload = "{\"title\":\"Xin chào\",\"body\":\"Nội dung\"}";

        DefaultMessage message = new DefaultMessage(
                channel.getBytes(StandardCharsets.UTF_8),
                payload.getBytes(StandardCharsets.UTF_8)
        );

        listener.onMessage(message, null);

        verify(sseEmitterRegistry).push(eq(accountId), eq("notification"), eq(payload));
    }
}
