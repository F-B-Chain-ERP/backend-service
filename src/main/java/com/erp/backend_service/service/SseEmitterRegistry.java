package com.erp.backend_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Quản lý kết nối SseEmitter theo từng tài khoản (in-memory).
 * Thread-safe: Một tài khoản có thể mở nhiều tab trình duyệt (nhiều emitter).
 */
@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    private final Map<UUID, List<SseEmitter>> emitterMap = new ConcurrentHashMap<>();

    /**
     * Đăng ký một emitter mới cho tài khoản.
     *
     * @param accountId ID tài khoản
     * @param timeoutMs Thời gian timeout (ms)
     * @return SseEmitter đã được gắn các callback dọn dẹp
     */
    public SseEmitter register(UUID accountId, long timeoutMs) {
        SseEmitter emitter = new SseEmitter(timeoutMs);

        emitterMap.computeIfAbsent(accountId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> removeEmitter(accountId, emitter);
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

        log.info("SSE client connected for account: {}. Total active emitters for user: {}",
                accountId, emitterMap.getOrDefault(accountId, List.of()).size());
        return emitter;
    }

    /**
     * Đẩy dữ liệu thông báo tới tất cả emitter đang hoạt động của tài khoản này.
     *
     * @param accountId ID tài khoản
     * @param payload Chuỗi JSON chứa thông báo
     */
    public void push(UUID accountId, String payload) {
        List<SseEmitter> emitters = emitterMap.get(accountId);
        if (emitters == null || emitters.isEmpty()) {
            log.debug("No active SSE emitters found for account: {}", accountId);
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(payload));
            } catch (Exception e) {
                log.warn("Lỗi gửi SSE cho account {}, tiến hành dọn dẹp emitter: {}", accountId, e.getMessage());
                removeEmitter(accountId, emitter);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
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
        for (Map.Entry<UUID, List<SseEmitter>> entry : emitterMap.entrySet()) {
            UUID accountId = entry.getKey();
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().name("ping").data("heartbeat"));
                } catch (Exception e) {
                    log.debug("SSE heartbeat failed for account {} (client likely disconnected), removing emitter: {}",
                            accountId, e.getMessage());
                    removeEmitter(accountId, emitter);
                    try {
                        emitter.completeWithError(e);
                    } catch (Exception ignored) {
                        // Already disconnected — nothing to do
                    }
                }
            }
        }
    }

    private void removeEmitter(UUID accountId, SseEmitter emitter) {
        List<SseEmitter> list = emitterMap.get(accountId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitterMap.remove(accountId);
            }
        }
    }

    public int getActiveConnectionCount() {
        return emitterMap.values().stream().mapToInt(List::size).sum();
    }
}
