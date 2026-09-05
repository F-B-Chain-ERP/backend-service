package com.erp.backend_service.controller;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.NotificationService;
import com.erp.backend_service.service.SseEmitterRegistry;
import com.erp.backend_service.util.RedisKeys;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.notification.NotificationResponse;
import com.erp.core.dto.response.notification.SseTicketResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Quản lý thông báo in-app và cung cấp luồng Server-Sent Events (SSE) theo thời gian thực.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L; // 30 phút

    private final NotificationService notificationService;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final StringRedisTemplate stringRedisTemplate;

    public NotificationController(
            NotificationService notificationService,
            SseEmitterRegistry sseEmitterRegistry,
            StringRedisTemplate stringRedisTemplate
    ) {
        this.notificationService = notificationService;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Cấp vé kết nối SSE dùng một lần (single-use ticket) có thời hạn 30 giây.
     * Yêu cầu người dùng đã xác thực bằng JWT Bearer.
     */
    @PostMapping("/sse-ticket")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SseTicketResponse>> generateSseTicket() {
        UUID accountId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));

        String ticket = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set(
                RedisKeys.sseTicket(ticket),
                accountId.toString(),
                Duration.ofSeconds(RedisKeys.SSE_TICKET_TTL_SECONDS)
        );

        return ResponseEntity.ok(ApiResponse.success(
                new SseTicketResponse(ticket, RedisKeys.SSE_TICKET_TTL_SECONDS)
        ));
    }

    /**
     * Endpoint kết nối Server-Sent Events (SSE).
     * Xác thực thông qua ticket ngắn hạn (single-use), không truyền trực tiếp JWT qua URL.
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeSse(@RequestParam("ticket") String ticket) {
        if (!StringUtils.hasText(ticket)) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED);
        }

        // Lấy và xóa vé khỏi Redis (chỉ dùng được một lần)
        String accountIdStr = stringRedisTemplate.opsForValue().getAndDelete(RedisKeys.sseTicket(ticket));
        if (!StringUtils.hasText(accountIdStr)) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED);
        }

        UUID accountId = UUID.fromString(accountIdStr);
        return sseEmitterRegistry.register(accountId, SSE_TIMEOUT_MS);
    }

    /**
     * Lấy danh sách thông báo chưa đọc của tài khoản hiện tại.
     */
    @GetMapping("/unread")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getUnread() {
        UUID accountId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));
        return ResponseEntity.ok(ApiResponse.success(notificationService.getUnreadNotifications(accountId)));
    }

    /**
     * Lấy danh sách thông báo gần đây của tài khoản hiện tại.
     */
    @GetMapping("/recent")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getRecent(
            @RequestParam(defaultValue = "20") int limit
    ) {
        UUID accountId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));
        return ResponseEntity.ok(ApiResponse.success(notificationService.getRecentNotifications(accountId, limit)));
    }

    /**
     * Đếm số lượng thông báo chưa đọc của tài khoản hiện tại.
     */
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount() {
        UUID accountId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));
        return ResponseEntity.ok(ApiResponse.success(notificationService.getUnreadCount(accountId)));
    }

    /**
     * Đánh dấu một thông báo là đã đọc.
     */
    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Boolean>> markAsRead(@PathVariable UUID id) {
        UUID accountId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));
        notificationService.markAsRead(id, accountId);
        return ResponseEntity.ok(ApiResponse.success(true));
    }

    /**
     * Đánh dấu tất cả thông báo của tài khoản là đã đọc.
     */
    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Boolean>> markAllAsRead() {
        UUID accountId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));
        notificationService.markAllAsRead(accountId);
        return ResponseEntity.ok(ApiResponse.success(true));
    }

    /**
     * Xóa một thông báo theo ID của tài khoản.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Boolean>> deleteNotification(@PathVariable UUID id) {
        UUID accountId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));
        notificationService.deleteNotification(id, accountId);
        return ResponseEntity.ok(ApiResponse.success(true));
    }

    /**
     * Xóa toàn bộ thông báo của tài khoản.
     */
    @DeleteMapping("/all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Boolean>> deleteAllNotifications() {
        UUID accountId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));
        notificationService.deleteAllNotifications(accountId);
        return ResponseEntity.ok(ApiResponse.success(true));
    }

    /**
     * Xóa các thông báo đã đọc của tài khoản.
     */
    @DeleteMapping("/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Boolean>> deleteReadNotifications() {
        UUID accountId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));
        notificationService.deleteReadNotifications(accountId);
        return ResponseEntity.ok(ApiResponse.success(true));
    }
}
