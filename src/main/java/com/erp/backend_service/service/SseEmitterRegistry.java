package com.erp.backend_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Quản lý kết nối SseEmitter theo từng tài khoản (in-memory) và hỗ trợ phân nhóm theo chi nhánh.
 * Thread-safe: Một tài khoản có thể mở nhiều tab trình duyệt (nhiều emitter).
 */
@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    public record SseClient(SseEmitter emitter, UUID branchId, boolean isStaff) {}

    private final Map<UUID, List<SseClient>> emitterMap = new ConcurrentHashMap<>();

    /**
     * Đăng ký một emitter mới cho tài khoản không gắn chi nhánh cụ thể (khách hàng hoặc admin chung).
     */
    public SseEmitter register(UUID accountId, long timeoutMs) {
        return register(accountId, null, false, timeoutMs);
    }

    /**
     * Đăng ký một emitter mới cho tài khoản có ngữ cảnh chi nhánh (nhân viên POS / quản lý chi nhánh).
     */
    public SseEmitter register(UUID accountId, UUID branchId, long timeoutMs) {
        return register(accountId, branchId, false, timeoutMs);
    }

    /**
     * Đăng ký một emitter mới với cờ xác định người dùng nội bộ (nhân viên/admin) hay khách hàng.
     *
     * @param accountId ID tài khoản (hoặc ID khách hàng)
     * @param branchId ID chi nhánh làm việc (nếu có)
     * @param isStaff true nếu là tài khoản nhân viên/admin, false nếu là khách hàng
     * @param timeoutMs Thời gian timeout (ms)
     * @return SseEmitter đã được gắn các callback dọn dẹp
     */
    public SseEmitter register(UUID accountId, UUID branchId, boolean isStaff, long timeoutMs) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        SseClient client = new SseClient(emitter, branchId, isStaff);

        emitterMap.computeIfAbsent(accountId, k -> new CopyOnWriteArrayList<>()).add(client);

        Runnable cleanup = () -> removeEmitter(accountId, client);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // Gửi handshake ban đầu để thông báo client kết nối thành công
        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data("CONNECTED"));
        } catch (IOException e) {
            log.warn("Không thể gửi INIT SSE cho account {}: {}", accountId, e.getMessage());
            cleanup.run();
        }

        log.info("SSE client connected for account: {} (branch: {}, isStaff: {}). Total active emitters for user: {}",
                accountId, branchId, isStaff, emitterMap.getOrDefault(accountId, List.of()).size());
        return emitter;
    }

    /**
     * Đẩy dữ liệu thông báo mặc định ("notification") tới tất cả emitter đang hoạt động của tài khoản này.
     */
    public void push(UUID accountId, String payload) {
        push(accountId, "notification", payload);
    }

    /**
     * Đẩy dữ liệu với tên sự kiện tùy chỉnh (ví dụ "notification", "order_event") tới tài khoản này.
     */
    public void push(UUID accountId, String eventName, String payload) {
        List<SseClient> clients = emitterMap.get(accountId);
        if (clients == null || clients.isEmpty()) {
            log.debug("No active SSE emitters found for account: {}", accountId);
            return;
        }

        for (SseClient client : clients) {
            try {
                client.emitter().send(SseEmitter.event()
                        .name(eventName)
                        .data(payload));
            } catch (Exception e) {
                log.warn("Lỗi gửi SSE cho account {}, tiến hành dọn dẹp emitter: {}", accountId, e.getMessage());
                removeEmitter(accountId, client);
                try {
                    client.emitter().complete();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Đẩy dữ liệu tới toàn bộ nhân viên đang trực thuộc chi nhánh này (hoặc admin toàn hệ thống không gắn branchId).
     *
     * @param branchId ID chi nhánh mục tiêu
     * @param eventName Tên sự kiện (ví dụ "order_event")
     * @param payload Chuỗi JSON
     */
    public void pushToBranch(UUID branchId, String eventName, String payload) {
        if (emitterMap.isEmpty()) {
            return;
        }

        for (Map.Entry<UUID, List<SseClient>> entry : emitterMap.entrySet()) {
            UUID accountId = entry.getKey();
            for (SseClient client : entry.getValue()) {
                // CHỈ gửi cho nhân viên/admin nội bộ (isStaff == true), tuyệt đối không gửi cho Khách hàng
                if (!client.isStaff()) {
                    continue;
                }

                // Nhận nếu cùng chi nhánh hoặc là admin toàn quyền (branchId == null)
                if (client.branchId() == null || Objects.equals(client.branchId(), branchId)) {
                    try {
                        client.emitter().send(SseEmitter.event()
                                .name(eventName)
                                .data(payload));
                    } catch (Exception e) {
                        log.warn("Lỗi broadcast SSE branch {} cho account {}, dọn dẹp emitter: {}",
                                branchId, accountId, e.getMessage());
                        removeEmitter(accountId, client);
                        try {
                            client.emitter().complete();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
    }

    /**
     * Định kỳ gửi ping để giữ kết nối không bị proxy / load balancer ngắt.
     */
    @Scheduled(fixedDelay = 25000)
    public void sendHeartbeat() {
        if (emitterMap.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, List<SseClient>> entry : emitterMap.entrySet()) {
            UUID accountId = entry.getKey();
            for (SseClient client : entry.getValue()) {
                try {
                    client.emitter().send(SseEmitter.event().name("ping").data("heartbeat"));
                } catch (Exception e) {
                    log.debug("SSE heartbeat failed for account {} (client likely disconnected), removing emitter: {}",
                            accountId, e.getMessage());
                    removeEmitter(accountId, client);
                    try {
                        client.emitter().completeWithError(e);
                    } catch (Exception ignored) {
                        // Already disconnected — nothing to do
                    }
                }
            }
        }
    }

    private void removeEmitter(UUID accountId, SseClient client) {
        List<SseClient> list = emitterMap.get(accountId);
        if (list != null) {
            list.remove(client);
            if (list.isEmpty()) {
                emitterMap.remove(accountId);
            }
        }
    }

    public int getActiveConnectionCount() {
        return emitterMap.values().stream().mapToInt(List::size).sum();
    }
}
