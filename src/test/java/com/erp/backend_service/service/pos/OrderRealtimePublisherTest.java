package com.erp.backend_service.service.pos;

import com.erp.backend_service.event.OrderRealtimeEvent;
import com.erp.backend_service.util.RedisKeys;
import tools.jackson.databind.ObjectMapper;
import com.erp.backend_service.service.NotificationResolverService;
import com.erp.backend_service.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderRealtimePublisherTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationResolverService notificationResolverService;

    private ObjectMapper objectMapper;
    private OrderRealtimePublisher publisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        publisher = new OrderRealtimePublisher(stringRedisTemplate, notificationService, objectMapper, notificationResolverService);
    }

    @Test
    @DisplayName("Nên broadcast tới branch, customer và shipper khi sự kiện có đủ thông tin")
    void shouldPublishToBranchCustomerAndShipper() {
        UUID orderId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID shipperId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        when(notificationResolverService.resolveBranchStaffAndAdmins(eq(branchId), isNull()))
                .thenReturn(Set.of(managerId));

        OrderRealtimeEvent event = new OrderRealtimeEvent(
                OrderRealtimeEvent.TYPE_ORDER_STATUS_CHANGED,
                orderId,
                "SO-202609-0001",
                branchId,
                customerId,
                shipperId,
                "PREPARING",
                "ASSIGNED",
                "PAID",
                "Đang pha chế",
                "Đơn hàng đang được chuẩn bị",
                Instant.now()
        );

        publisher.onOrderRealtimeEvent(event);

        // 1. Phải gửi tới kênh chi nhánh
        verify(stringRedisTemplate).convertAndSend(eq(RedisKeys.branchNotificationChannel(branchId)), any(String.class));

        // 2. Phải gửi tới kênh khách hàng
        verify(stringRedisTemplate).convertAndSend(eq(RedisKeys.notificationChannel(customerId)), any(String.class));

        // 3. Phải gửi tới kênh tài xế
        verify(stringRedisTemplate).convertAndSend(eq(RedisKeys.notificationChannel(shipperId)), any(String.class));

        // 4. Phải tạo notification cá nhân cho khách hàng, shipper và nhân viên chi nhánh.
        verify(notificationService).notifyCustomer(eq(customerId), any(String.class), any(String.class));
        verify(notificationService).notifyAccount(eq(shipperId), any(String.class), any(String.class));
        verify(notificationService).notifyAccount(eq(managerId), any(String.class), any(String.class));
    }

    @Test
    @DisplayName("Nên bỏ qua an toàn khi event là null")
    void shouldHandleNullEventSafely() {
        publisher.onOrderRealtimeEvent(null);
        verifyNoInteractions(stringRedisTemplate);
        verifyNoInteractions(notificationService);
    }
}
