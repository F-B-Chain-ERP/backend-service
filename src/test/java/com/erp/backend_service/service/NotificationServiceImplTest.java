package com.erp.backend_service.service;

import com.erp.backend_service.repository.NotificationRepository;
import com.erp.backend_service.service.impl.NotificationServiceImpl;
import com.erp.backend_service.util.RedisKeys;
import com.erp.core.domain.Notification;
import com.erp.core.dto.response.notification.NotificationResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(
                notificationRepository,
                stringRedisTemplate,
                objectMapper
        );
    }

    @Test
    @DisplayName("notifyAccount should save notification to DB and publish to Redis Pub/Sub")
    void testNotifyAccount() {
        UUID accountId = UUID.randomUUID();
        String title = "Đơn mua hàng mới";
        String body = "PO-202609-001 đang chờ duyệt";

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            return n;
        });

        notificationService.notifyAccount(accountId, title, body);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertEquals(accountId, saved.getAccountId());
        assertEquals(title, saved.getTitle());
        assertEquals(body, saved.getBody());
        assertEquals("IN_APP", saved.getChannel());

        String expectedChannel = RedisKeys.notificationChannel(accountId);
        verify(stringRedisTemplate).convertAndSend(eq(expectedChannel), any(String.class));
    }

    @Test
    @DisplayName("getUnreadNotifications should return unread items")
    void testGetUnreadNotifications() {
        UUID accountId = UUID.randomUUID();
        Notification n = new Notification();
        n.setAccountId(accountId);
        n.setTitle("Test");
        n.setBody("Content");
        n.setStatus("PENDING");

        when(notificationRepository.findByAccountIdAndReadAtIsNullOrderByCreatedAtDesc(accountId))
                .thenReturn(List.of(n));

        List<NotificationResponse> list = notificationService.getUnreadNotifications(accountId);
        assertEquals(1, list.size());
        assertEquals("Test", list.get(0).title());
    }

    @Test
    @DisplayName("Notification with PO code should populate actionUrl")
    void testActionUrlGeneration() {
        UUID accountId = UUID.randomUUID();
        Notification n = new Notification();
        n.setAccountId(accountId);
        n.setTitle("Duyệt đơn");
        n.setBody("Đơn mua hàng PO-202609-0020 cần phê duyệt");
        n.setStatus("PENDING");

        when(notificationRepository.findByAccountIdAndReadAtIsNullOrderByCreatedAtDesc(accountId))
                .thenReturn(List.of(n));

        List<NotificationResponse> list = notificationService.getUnreadNotifications(accountId);
        assertEquals(1, list.size());
        assertEquals("/admin/procurement/purchase-orders/list?code=PO-202609-0020", list.get(0).actionUrl());
    }

    @Test
    @DisplayName("markAsRead should call repository markAsRead")
    void testMarkAsRead() {
        UUID notificationId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        notificationService.markAsRead(notificationId, accountId);
        verify(notificationRepository).markAsRead(eq(notificationId), eq(accountId), any());
    }

    @Test
    @DisplayName("deleteNotification should call repository deleteByIdAndAccountId")
    void testDeleteNotification() {
        UUID notificationId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        notificationService.deleteNotification(notificationId, accountId);
        verify(notificationRepository).deleteByIdAndAccountId(notificationId, accountId);
    }

    @Test
    @DisplayName("deleteAllNotifications should call repository deleteAllByAccountId")
    void testDeleteAllNotifications() {
        UUID accountId = UUID.randomUUID();

        notificationService.deleteAllNotifications(accountId);
        verify(notificationRepository).deleteAllByAccountId(accountId);
    }
}
