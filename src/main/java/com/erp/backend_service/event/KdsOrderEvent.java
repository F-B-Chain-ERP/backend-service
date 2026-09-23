package com.erp.backend_service.event;

import java.util.UUID;

/** Side-effect KDS chạy sau khi transaction Order/Delivery đã commit. */
public record KdsOrderEvent(Action action, UUID orderId, String orderStatus, String reason) {
    public enum Action {
        CREATE,
        SYNC,
        SERVE,
        CANCEL
    }
}
