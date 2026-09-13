package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.*;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.DeliveryService;
import com.erp.core.domain.*;
import com.erp.core.dto.request.pos.*;
import com.erp.core.dto.response.pos.*;
import com.erp.core.enums.PrincipalType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.Locale;
import java.util.Objects;

@Service
public class DeliveryServiceImpl implements DeliveryService {
    private static final String ACTIVE = "ACTIVE";
    private final OrderDeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final BranchVariantDailyStockRepository stockRepository;
    private final DataScopeHelper dataScopeHelper;

    public DeliveryServiceImpl(OrderDeliveryRepository deliveryRepository, OrderRepository orderRepository,
                               OrderItemRepository orderItemRepository,
                               OrderStatusHistoryRepository historyRepository,
                               AccountRepository accountRepository,
                               AccountRoleRepository accountRoleRepository,
                               BranchVariantDailyStockRepository stockRepository,
                               DataScopeHelper dataScopeHelper) {
        this.deliveryRepository = deliveryRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.historyRepository = historyRepository;
        this.accountRepository = accountRepository;
        this.accountRoleRepository = accountRoleRepository;
        this.stockRepository = stockRepository;
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
        if (!Set.of("PENDING", "FAILED").contains(d.getStatus())) {
            throw new BaseException(ErrorCode.ORDER_400_DELIVERY_FAILED,
                                    "Trạng thái giao hàng không cho phép phân công.");
        }
        Account shipper = accountRepository.findById(request.shipperId())
                                           .orElseThrow(() -> new BaseException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (shipper.getStatus() != com.erp.core.enums.EntityStatus.ACTIVE ||
            (!dataScopeHelper.isAllSystem() && !o.getBranchId().equals(shipper.getPrimaryBranchId()))) {
            throw new BaseException(ErrorCode.ORDER_403_OUT_OF_SCOPE);
        }
        if (accountRoleRepository.findByAccountId(shipper.getId()).isEmpty()) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "Tài khoản không có vai trò phù hợp để giao hàng.");
        }
        d.setShipperId(shipper.getId());
        d.setAssignedAt(Instant.now());
        d.setStatus("ASSIGNED");
        d.setFailReason(null);
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
        String target = request.status().trim().toUpperCase(Locale.ROOT);
        String old = d.getStatus();
        if (!valid(old, target)) {
            throw new BaseException(ErrorCode.ORDER_400_DELIVERY_FAILED, "Chuyển trạng thái giao hàng không hợp lệ.");
        }
        Instant now = Instant.now();
        d.setStatus(target);
        if (request.note() != null) {
            d.setDeliveryNote(request.note());
        }
        switch (target) {
            case "PICKED_UP" -> d.setPickedUpAt(now);
            case "DELIVERING" -> {
                d.setAssignedAt(d.getAssignedAt() == null ? now : d.getAssignedAt());
                String previousOrderStatus = o.getStatus();
                o.setStatus("DELIVERING");
                o.setDeliveringAt(now);
                orderRepository.save(o);
                writeHistory(o, previousOrderStatus, "DELIVERING", request.note());
            }
            case "DELIVERED" -> {
                d.setDeliveredAt(now);
                if ("PAID".equalsIgnoreCase(o.getPaymentStatus()) &&
                    Set.of("DELIVERING", "READY").contains(o.getStatus())) {
                    String previousOrderStatus = o.getStatus();
                    o.setStatus("COMPLETED");
                    o.setCompletedAt(now);
                    deductStock(o);
                    orderRepository.save(o);
                    writeHistory(o, previousOrderStatus, "COMPLETED", request.note());
                }
            }
            case "FAILED" -> {
                d.setFailedAt(now);
                d.setFailReason(request.failReason());
                if ("DELIVERING".equals(o.getStatus())) {
                    String previousOrderStatus = o.getStatus();
                    o.setStatus("ASSIGNED");
                    orderRepository.save(o);
                    writeHistory(o, previousOrderStatus, "ASSIGNED", "Giao hàng thất bại, quay lại trạng thái đã phân công.");
                }
            }
            default -> {
            }
        }
        deliveryRepository.save(d);
        return new DeliveryStatusResponse(d.getId(), o.getId(), old, target, now);
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

    private boolean valid(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return (a.equals("PENDING") && b.equals("ASSIGNED")) || (a.equals("ASSIGNED") && b.equals("PICKED_UP")) ||
            (a.equals("PICKED_UP") && b.equals("DELIVERING")) || (a.equals("DELIVERING") && b.equals("DELIVERED")) ||
            (a.equals("DELIVERING") && b.equals("FAILED")) || (a.equals("FAILED") && b.equals("ASSIGNED"));
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

    private void deductStock(Order order) {
        for (OrderItem oi : orderItemRepository.findByOrderIdAndStatusOrderByCreatedAtAsc(order.getId(), ACTIVE)) {
            if (oi.getVariantId() != null) {
                BranchVariantDailyStock s =
                    stockRepository.findByBranchIdAndVariantIdAndBusinessDateAndStatus(order.getBranchId(),
                                                                                       oi.getVariantId(),
                                                                                       LocalDate.now(), ACTIVE)
                                   .orElseThrow(() -> new BaseException(ErrorCode.ORDER_400_INVALID_QUANTITY));
                if (s.getRemainingQuantity() < oi.getQuantity()) {
                    throw new BaseException(ErrorCode.ORDER_400_INVALID_QUANTITY);
                }
                s.setRemainingQuantity(s.getRemainingQuantity() - oi.getQuantity());
                s.setSoldQuantity(s.getSoldQuantity() + oi.getQuantity());
                stockRepository.save(s);
            }
        }
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
