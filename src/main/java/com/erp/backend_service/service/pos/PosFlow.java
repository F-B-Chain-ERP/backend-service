package com.erp.backend_service.service.pos;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;

import java.util.Locale;

/**
 * Vòng đời POS gom 1 file: khai báo status (3 enum) + luật parse/chuyển trạng thái.
 * Entity vẫn lưu String (server là chuẩn, DB có thể bẩn) nên parse ở đây,
 * lỗi ném BaseException nghiệp vụ thay vì 500 của Hibernate.
 */
public final class PosFlow {

    private PosFlow() {
    }

    public enum Order {
        PENDING,
        CONFIRMED,
        PREPARING,
        READY,
        DELIVERING,
        COMPLETED,
        CANCELLED,
        REJECTED
    }

    public enum Delivery {
        PENDING,
        ASSIGNED,
        PICKED_UP,
        DELIVERING,
        DELIVERED,
        FAILED,
        CANCELLED
    }

    public enum Payment {
        UNPAID,
        PAID,
        REFUNDED
    }

    public enum Kds {
        QUEUED,
        PREPARING,
        READY,
        SERVED,
        CANCELLED
    }

    public static Order parseOrder(String code) {
        if (code == null || code.isBlank()) {
            throw new BaseException(ErrorCode.ORDER_400_INVALID_STATUS_TRANSITION);
        }
        try {
            return Order.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BaseException(ErrorCode.ORDER_400_INVALID_STATUS_TRANSITION,
                "Trạng thái đơn không hợp lệ: " + code);
        }
    }

    public static Delivery parseDelivery(String code) {
        if (code == null || code.isBlank()) {
            throw new BaseException(ErrorCode.ORDER_400_DELIVERY_FAILED, "Trạng thái giao hàng không hợp lệ.");
        }
        try {
            return Delivery.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BaseException(ErrorCode.ORDER_400_DELIVERY_FAILED,
                "Trạng thái giao hàng không hợp lệ: " + code);
        }
    }

    public static Payment parsePayment(String code) {
        if (code == null || code.isBlank()) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "Trạng thái thanh toán không hợp lệ.");
        }
        try {
            return Payment.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BaseException(ErrorCode.INVALID_REQUEST,
                "Trạng thái thanh toán không hợp lệ: " + code);
        }
    }

    /**
     * Luật đơn: PICKUP kết ở READY -> COMPLETED, DELIVERY qua READY -> DELIVERING -> COMPLETED.
     * Dùng chung cho OrderService (updateStatus) và DeliveryService (kéo order theo giao hàng).
     */
    public static boolean canOrderTransition(Order from, Order to) {
        if (from == null || to == null) {
            return false;
        }
        return switch (from) {
            case PENDING -> to == Order.CONFIRMED || to == Order.CANCELLED || to == Order.REJECTED;
            case CONFIRMED -> to == Order.PREPARING || to == Order.CANCELLED || to == Order.REJECTED;
            case PREPARING -> to == Order.READY || to == Order.CANCELLED || to == Order.REJECTED;
            case READY -> to == Order.DELIVERING || to == Order.COMPLETED;
            case DELIVERING -> to == Order.COMPLETED;
            default -> false;
        };
    }

    public static void requireOrderTransition(Order from, Order to) {
        if (!canOrderTransition(from, to)) {
            throw new BaseException(ErrorCode.ORDER_400_INVALID_STATUS_TRANSITION);
        }
    }

    /**
     * Luật giao: ASSIGNED chỉ qua assign() có shipper,
     * updateStatus chỉ chạy ASSIGNED -> PICKED_UP -> DELIVERING -> DELIVERED / FAILED.
     */
    public static boolean canDeliveryTransition(Delivery from, Delivery to) {
        if (from == null || to == null) {
            return false;
        }
        return switch (from) {
            case ASSIGNED -> to == Delivery.PICKED_UP;
            case PICKED_UP -> to == Delivery.DELIVERING;
            case DELIVERING -> to == Delivery.DELIVERED || to == Delivery.FAILED;
            default -> false;
        };
    }

    public static Kds parseKds(String code) {
        if (code == null || code.isBlank()) {
            throw new BaseException(ErrorCode.KDS_400_INVALID_STATUS_TRANSITION);
        }
        try {
            return Kds.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BaseException(ErrorCode.KDS_400_INVALID_STATUS_TRANSITION,
                "Trạng thái bếp không hợp lệ: " + code);
        }
    }

    /**
     * Luật bếp (1 trạm BAR cố định, không sửa DB):
     * QUEUED -> PREPARING -> READY -> SERVED, các trạng thái hoạt động -> CANCELLED.
     */
    public static boolean canKdsTransition(Kds from, Kds to) {
        if (from == null || to == null) {
            return false;
        }
        return switch (from) {
            case QUEUED -> to == Kds.PREPARING || to == Kds.CANCELLED;
            case PREPARING -> to == Kds.READY || to == Kds.CANCELLED;
            case READY -> to == Kds.SERVED || to == Kds.CANCELLED;
            default -> false;
        };
    }

    public static void requireKdsTransition(Kds from, Kds to) {
        if (!canKdsTransition(from, to)) {
            throw new BaseException(ErrorCode.KDS_400_INVALID_STATUS_TRANSITION);
        }
    }
}
