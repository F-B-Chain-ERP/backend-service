package com.erp.backend_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SseEmitterRegistryTest {

    private SseEmitterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry();
    }

    @Test
    @DisplayName("register should increment active connections count")
    void register_ShouldIncrementActiveConnections() {
        UUID customerId = UUID.randomUUID();
        SseEmitter emitter = registry.register(customerId, null, false, 5000L);

        assertNotNull(emitter);
        assertEquals(1, registry.getActiveConnectionCount());
    }

    @Test
    @DisplayName("pushToBranch should not throw error and deliver safely")
    void pushToBranch_ShouldExecuteSafely() {
        UUID branchId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        // Staff client in branch
        registry.register(staffId, branchId, true, 5000L);
        // Customer client with no branch
        registry.register(customerId, null, false, 5000L);

        assertEquals(2, registry.getActiveConnectionCount());

        // pushToBranch should execute without throwing, customer is filtered out internally
        registry.pushToBranch(branchId, "order_event", "{\"test\":true}");
        registry.push(customerId, "order_event", "{\"direct\":true}");
    }
}
