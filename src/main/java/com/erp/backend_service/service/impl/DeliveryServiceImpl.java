package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.*;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.DeliveryService;
import com.erp.backend_service.service.pos.PosFlow;
import com.erp.core.domain.*;
import com.erp.core.dto.request.pos.*;
import com.erp.core.dto.response.pos.*;
import com.erp.core.enums.EntityStatus;
import com.erp.core.enums.PrincipalType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.Objects;

@Service
public class DeliveryServiceImpl implements DeliveryService {
    private static final String ACTIVE = "ACTIVE";
    private final OrderDeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final DataScopeHelper dataScopeHelper;

    public DeliveryServiceImpl(OrderDeliveryRepository deliveryRepository, OrderRepository orderRepository,
                               OrderStatusHistoryRepository historyRepository,
                               AccountRepository accountRepository,
                               AccountRoleRepository accountRoleRepository,
                               DataScopeHelper dataScopeHelper) {
        this.deliveryRepository = deliveryRepository;
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.accountRepository = accountRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.dataScopeHelper = dataScopeHelper;
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryResponse getByOrderId(UUID orderId) {
        requireViewPermission();
        Order o = accessibleOrder(orderId);
        OrderDelivery d = deliveryRepository.findByOrderId(o.getId()).orElseThrow(
            () -> new BaseException(ErrorCode.ORDER_404_DELIVERY_NOT_FOUND));
        return toResponse(d);
    }

    @Override
    @Transactional
    public DeliveryResponse assign(UUID orderId, AssignDeliveryRequest request) {
        requireUpdatePermission();
        Order o = accessibleOrder(orderId);
        if (!"DELIVERY".equals(o.getOrderType())) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "Đơn hàng không phải đơn giao.");
        }
        OrderDelivery d = deliveryRepository.findByOrderId(o.getId()).orElseThrow(
            () -> new BaseException(ErrorCode.ORDER_404_DELIVERY_NOT_FOUND));
        PosFlow.Delivery current = PosFlow.parseDelivery(d.getStatus());
        if (!(current == PosFlow.Delivery.PENDING || current == PosFlow.Delivery.FAILED ||
            current == PosFlow.Delivery.ASSIGNED)) {
            throw new BaseException(ErrorCode.ORDER_400_DELIVERY_FAILED,
                                    "Trạng thái giao hàng không cho phép phân công.");
        }
        Account shipper = accountRepository.findById(request.shipperId())
                                           .orElseThrow(() -> new BaseException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (shipper.getStatus() != com.erp.core.enums.EntityStatus.ACTIVE ||
            (!dataScopeHelper.isAllSystem() && !o.getBranchId().equals(shipper.getPrimaryBranchId()))) {
            throw new BaseException(ErrorCode.ORDER_403_OUT_OF_SCOPE);
        }
        // Giả thiết E6: DB chưa có role SHIPPER riêng nên không check code cứng.
        // Check effective (chưa hết hạn) thay vì findByAccountId thô như cũ (lọt expired).
        // TODO: tạo role SHIPPER + check pos:delivery:update trên role khi có.
        var effective = accountRoleRepository.findEffectiveByAccountId(shipper.getId(), EntityStatus.ACTIVE,
            Instant.now());
        if (effective.isEmpty()) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "Tài khoản không có vai trò phù hợp để giao hàng.");
        }
        // Cho đổi shipper khi đã ASSIGNED (ops cần); gán lại từ FAILED thì xóa dấu thất bại cũ.
        d.setShipperId(shipper.getId());
        d.setAssignedAt(Instant.now());
        d.setStatus(PosFlow.Delivery.ASSIGNED.name());
        d.setFailReason(null);
        d.setFailedAt(null);
        return toResponse(deliveryRepository.save(d));
    }

    @Override
    @Transactional
    public DeliveryStatusResponse updateStatus(UUID orderId, UpdateDeliveryStatusRequest request) {
        requireUpdatePermission();
        if (request.status() == null || request.status().isBlank()) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "Trạng thái không được để trống.");
        }
        Order o = accessibleOrder(orderId);
        OrderDelivery d = deliveryRepository.findByOrderId(o.getId()).orElseThrow(
            () -> new BaseException(ErrorCode.ORDER_404_DELIVERY_NOT_FOUND));
        PosFlow.Delivery target = PosFlow.parseDelivery(request.status());
        PosFlow.Delivery current = PosFlow.parseDelivery(d.getStatus());
        String old = d.getStatus();
        if (!PosFlow.canDeliveryTransition(current, target)) {
            throw new BaseException(ErrorCode.ORDER_400_DELIVERY_FAILED, "Chuyển trạng thái giao hàng không hợp lệ.");
        }
        Instant now = Instant.now();
        d.setStatus(target.name());
        if (request.note() != null) {
            d.setDeliveryNote(request.note());
        }
        switch (target) {
            case PICKED_UP -> d.setPickedUpAt(now);
            case DELIVERING -> {
                d.setAssignedAt(d.getAssignedAt() == null ? now : d.getAssignedAt());
                // Giả thiết E2: order phải READY mới được đi giao, chặn nhảy cóc PENDING->DELIVERING.
                PosFlow.Order orderCurrent = PosFlow.parseOrder(o.getStatus());
                if (!(orderCurrent == PosFlow.Order.READY || orderCurrent == PosFlow.Order.DELIVERING)) {
                    throw new BaseException(ErrorCode.ORDER_400_INVALID_STATUS_TRANSITION,
                        "Đơn hàng phải ở trạng thái READY mới được giao.");
                }
                if (orderCurrent == PosFlow.Order.READY) {
                    String previousOrderStatus = o.getStatus();
                    o.setStatus(PosFlow.Order.DELIVERING.name());
                    o.setDeliveringAt(now);
                    orderRepository.save(o);
                    writeHistory(o, previousOrderStatus, PosFlow.Order.DELIVERING.name(), request.note());
                }
            }
            case DELIVERED -> {
                d.setDeliveredAt(now);
                // Giả thiết E4: COD giao xong coi như thu tiền mặt, khỏi treo đơn.
                if ("COD".equals(o.getPaymentMethod())
                    && PosFlow.Payment.UNPAID.name().equalsIgnoreCase(o.getPaymentStatus())) {
                    o.setPaymentStatus(PosFlow.Payment.PAID.name());
                }
                // Tồn đã reserve ở Order CONFIRMED nên không trừ lần 2 (xóa deductStock cũ - lỗi ẩn double-deduct).
                PosFlow.Order orderCurrent = PosFlow.parseOrder(o.getStatus());
                if (PosFlow.Payment.PAID.name().equalsIgnoreCase(o.getPaymentStatus()) &&
                    (orderCurrent == PosFlow.Order.DELIVERING || orderCurrent == PosFlow.Order.READY)) {
                    String previousOrderStatus = o.getStatus();
                    o.setStatus(PosFlow.Order.COMPLETED.name());
                    o.setCompletedAt(now);
                    orderRepository.save(o);
                    writeHistory(o, previousOrderStatus, PosFlow.Order.COMPLETED.name(), request.note());
                }
            }
            case FAILED -> {
                if (request.failReason() == null || request.failReason().isBlank()) {
                    throw new BaseException(ErrorCode.INVALID_REQUEST, "Giao thất bại phải có lý do.");
                }
                d.setFailedAt(now);
                d.setFailReason(request.failReason());
                // Giả thiết E1: ASSIGNED là status delivery, không phải order (ck_orders_status).
                // Giao thất bại -> order về READY để gán lại, không phải ASSIGNED bẩn.
                if (PosFlow.parseOrder(o.getStatus()) == PosFlow.Order.DELIVERING) {
                    String previousOrderStatus = o.getStatus();
                    o.setStatus(PosFlow.Order.READY.name());
                    orderRepository.save(o);
                    writeHistory(o, previousOrderStatus, PosFlow.Order.READY.name(),
                        "Giao hàng thất bại, chuyển về READY để giao lại.");
                }
            }
            default -> {
            }
        }
        deliveryRepository.save(d);
        return new DeliveryStatusResponse(d.getId(), o.getId(), old, target.name(), now);
    }

    private void requireViewPermission() {
        if (isCustomer()) {
            return;
        }
        if (!SecurityUtils.hasPermission("pos:delivery:view") && !SecurityUtils.hasPermission("delivery:view")) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
    }

    private void requireUpdatePermission() {
        if (!SecurityUtils.hasPermission("pos:delivery:update") && !SecurityUtils.hasPermission("delivery:update")) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
    }

    private boolean isCustomer() {
        return SecurityUtils.getCurrentPrincipalType().orElse(null) == PrincipalType.CUSTOMER;
    }

    private Order accessibleOrder(UUID id) {
        Order o =
            orderRepository.findById(id).orElseThrow(() -> new BaseException(ErrorCode.ORDER_404_ORDER_NOT_FOUND));
        PrincipalType type = SecurityUtils.getCurrentPrincipalType().orElse(null);
        UUID pid =
            SecurityUtils.getCurrentPrincipalId().orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));
        if (type == PrincipalType.CUSTOMER) {
            if (!Objects.equals(o.getCustomerId(), pid)) {
                throw new BaseException(ErrorCode.CROSS_SCOPE_DENIED);
            }
        } else {
            dataScopeHelper.enforceBranchAccess(o.getBranchId());
        }
        return o;
    }

    private DeliveryResponse toResponse(OrderDelivery d) {
        return new DeliveryResponse(d.getId(), d.getOrderId(), d.getShipperId(), d.getReceiverName(),
                                    d.getReceiverPhone(), d.getDeliveryAddress(), d.getDeliveryNote(),
                                    d.getDeliveryFee(), d.getStatus(), d.getAssignedAt(), d.getPickedUpAt(),
                                    d.getDeliveredAt(), d.getFailedAt(), d.getFailReason());
    }

    private void writeHistory(Order order, String oldStatus, String newStatus, String note) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(order.getId());
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedBy(SecurityUtils.getCurrentPrincipalId().orElse(null));
        history.setChangedAt(Instant.now());
        history.setReason(note);
        history.setStatus(ACTIVE);
        historyRepository.save(history);
    }
}
