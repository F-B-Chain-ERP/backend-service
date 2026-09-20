package com.erp.backend_service.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện thời gian thực khi đơn hàng hoặc thông tin giao hàng thay đổi trạng thái.
 */
public record OrderRealtimeEvent(
        String eventType,
        UUID orderId,
        String orderCode,
        UUID branchId,
        UUID customerId,
        UUID shipperId,
        String orderStatus,
        String deliveryStatus,
        String paymentStatus,
        String title,
        String message,
        Instant timestamp
) {
    public static final String TYPE_ORDER_CREATED = "ORDER_CREATED";
    public static final String TYPE_ORDER_STATUS_CHANGED = "ORDER_STATUS_CHANGED";
    public static final String TYPE_DELIVERY_ASSIGNED = "DELIVERY_ASSIGNED";
    public static final String TYPE_DELIVERY_STATUS_CHANGED = "DELIVERY_STATUS_CHANGED";
}
