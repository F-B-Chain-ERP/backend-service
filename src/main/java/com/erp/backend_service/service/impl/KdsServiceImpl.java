package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.KdsTicketItemRepository;
import com.erp.backend_service.repository.KdsTicketRepository;
import com.erp.backend_service.repository.KdsTicketSpecifications;
import com.erp.backend_service.repository.OrderItemRepository;
import com.erp.backend_service.repository.OrderRepository;
import com.erp.backend_service.repository.OrderStatusHistoryRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.KdsService;
import com.erp.backend_service.service.pos.PosFlow;
import com.erp.core.domain.KdsTicket;
import com.erp.core.domain.KdsTicketItem;
import com.erp.core.domain.Order;
import com.erp.core.domain.OrderItem;
import com.erp.core.domain.OrderStatusHistory;
import com.erp.core.dto.request.pos.KdsTicketItemProgressRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.pos.KdsTicketItemResponse;
import com.erp.core.dto.response.pos.KdsTicketResponse;
import com.erp.core.dto.response.pos.KdsTicketSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class KdsServiceImpl implements KdsService {

    public static final String STATION_BAR = "BAR";

    private final KdsTicketRepository ticketRepository;
    private final KdsTicketItemRepository ticketItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final DataScopeHelper dataScopeHelper;

    /** Chống trùng queue_no khi không có unique DB: khóa theo branch theo ngày. */
    private final ConcurrentHashMap<String, Object> queueLocks = new ConcurrentHashMap<>();

    public KdsServiceImpl(KdsTicketRepository ticketRepository,
                          KdsTicketItemRepository ticketItemRepository,
                          OrderRepository orderRepository,
                          OrderItemRepository orderItemRepository,
                          OrderStatusHistoryRepository historyRepository,
                          DataScopeHelper dataScopeHelper) {
        this.ticketRepository = ticketRepository;
        this.ticketItemRepository = ticketItemRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.historyRepository = historyRepository;
        this.dataScopeHelper = dataScopeHelper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<KdsTicketSummaryResponse> list(UUID branchId, String status, LocalDate fromDate,
                                                       LocalDate toDate, int page, int size) {
        requireViewPermission();
        if (page < 0 || size < 1 || size > 100) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "page/size không hợp lệ.");
        }
        UUID effectiveBranch = dataScopeHelper.resolveEffectiveBranchId(branchId);
        Instant from = fromDate == null ? null : fromDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant to = toDate == null ? null : toDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (normalizedStatus != null) {
            PosFlow.parseKds(normalizedStatus);
        }
        Page<KdsTicket> p = ticketRepository.findAll(
            KdsTicketSpecifications.filter(effectiveBranch, normalizedStatus, from, to),
            PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt")));
        Map<UUID, Order> orders = ordersOf(p.getContent());
        Map<UUID, Long> itemCounts = itemCountsOf(p.getContent());
        return new PageResponse<>(p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(),
            p.getContent().stream().map(t -> toSummary(t, orders.get(t.getOrderId()),
                itemCounts.getOrDefault(t.getId(), 0L))).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public KdsTicketResponse get(UUID id) {
        requireViewPermission();
        KdsTicket t = ticketRepository.findById(id)
            .orElseThrow(() -> new BaseException(ErrorCode.KDS_404_TICKET_NOT_FOUND));
        dataScopeHelper.enforceBranchAccess(t.getBranchId());
        return toResponse(t);
    }

    @Override
    @Transactional(readOnly = true)
    public KdsTicketResponse getByOrderId(UUID orderId) {
        requireViewPermission();
        KdsTicket t = ticketRepository.findByOrderId(orderId)
            .orElseThrow(() -> new BaseException(ErrorCode.KDS_404_TICKET_NOT_FOUND));
        dataScopeHelper.enforceBranchAccess(t.getBranchId());
        return toResponse(t);
    }

    @Override
    @Transactional
    public KdsTicketResponse createOnOrderConfirmed(UUID orderId) {
        Order o = orderRepository.findById(orderId)
            .orElseThrow(() -> new BaseException(ErrorCode.ORDER_404_ORDER_NOT_FOUND));
        // Idempotent: 1 order chỉ 1 ticket BAR.
        if (ticketRepository.findByOrderId(o.getId()).isPresent()) {
            return toResponse(ticketRepository.findByOrderId(o.getId()).get());
        }
        List<OrderItem> items = orderItemRepository.findByOrderIdAndStatusOrderByCreatedAtAsc(o.getId(), "ACTIVE");
        if (items.isEmpty()) {
            throw new BaseException(ErrorCode.ORDER_400_CART_EMPTY, "Đơn hàng không có món để tạo phiếu bếp.");
        }
        int queueNo = nextQueueNo(o.getBranchId());
        KdsTicket t = new KdsTicket();
        t.setOrderId(o.getId());
        t.setBranchId(o.getBranchId());
        t.setStation(STATION_BAR);
        t.setQueueNo(queueNo);
        t.setStatus(PosFlow.Kds.QUEUED.name());
        t = ticketRepository.save(t);
        for (OrderItem oi : items) {
            KdsTicketItem ti = new KdsTicketItem();
            ti.setKdsTicketId(t.getId());
            ti.setOrderItemId(oi.getId());
            ti.setPreparedQuantity(0);
            ti.setStatus(PosFlow.Kds.QUEUED.name());
            ticketItemRepository.save(ti);
        }
        return toResponse(t);
    }

    @Override
    @Transactional
    public void cancelByOrderId(UUID orderId, String reason) {
        ticketRepository.findByOrderId(orderId).ifPresent(t -> {
            PosFlow.Kds current = PosFlow.parseKds(t.getStatus());
            if (current == PosFlow.Kds.CANCELLED || current == PosFlow.Kds.SERVED) {
                return;
            }
            t.setStatus(PosFlow.Kds.CANCELLED.name());
            ticketRepository.save(t);
            List<KdsTicketItem> items = ticketItemRepository.findByKdsTicketIdOrderByCreatedAtAsc(t.getId());
            for (KdsTicketItem ti : items) {
                PosFlow.Kds ic = PosFlow.parseKds(ti.getStatus());
                if (ic != PosFlow.Kds.CANCELLED && ic != PosFlow.Kds.SERVED) {
                    ti.setStatus(PosFlow.Kds.CANCELLED.name());
                    ticketItemRepository.save(ti);
                }
            }
        });
    }

    @Override
    @Transactional
    public KdsTicketResponse start(UUID id) {
        requireUpdatePermission();
        KdsTicket t = accessibleTicket(id);
        transition(t, PosFlow.Kds.PREPARING);
        t.setStartedAt(Instant.now());
        ticketRepository.save(t);
        markItems(t.getId(), PosFlow.Kds.PREPARING);
        syncOrderStatus(t, PosFlow.Order.PREPARING);
        return toResponse(t);
    }

    @Override
    @Transactional
    public KdsTicketResponse ready(UUID id) {
        requireUpdatePermission();
        KdsTicket t = accessibleTicket(id);
        transition(t, PosFlow.Kds.READY);
        t.setReadyAt(Instant.now());
        ticketRepository.save(t);
        markItems(t.getId(), PosFlow.Kds.READY);
        syncOrderStatus(t, PosFlow.Order.READY);
        return toResponse(t);
    }

    @Override
    @Transactional
    public KdsTicketResponse serve(UUID id) {
        requireUpdatePermission();
        KdsTicket t = accessibleTicket(id);
        transition(t, PosFlow.Kds.SERVED);
        t.setServedAt(Instant.now());
        ticketRepository.save(t);
        markItems(t.getId(), PosFlow.Kds.SERVED);
        return toResponse(t);
    }

    @Override
    @Transactional
    public KdsTicketResponse progressItem(UUID itemId, KdsTicketItemProgressRequest request) {
        requireUpdatePermission();
        KdsTicketItem ti = ticketItemRepository.findById(itemId)
            .orElseThrow(() -> new BaseException(ErrorCode.KDS_404_TICKET_ITEM_NOT_FOUND));
        KdsTicket t = accessibleTicket(ti.getKdsTicketId());
        OrderItem oi = orderItemRepository.findById(ti.getOrderItemId())
            .orElseThrow(() -> new BaseException(ErrorCode.ORDER_404_ORDER_NOT_FOUND));
        PosFlow.Kds current = PosFlow.parseKds(ti.getStatus());
        if (current == PosFlow.Kds.CANCELLED || current == PosFlow.Kds.SERVED) {
            throw new BaseException(ErrorCode.KDS_400_INVALID_STATUS_TRANSITION);
        }
        if (request.preparedQuantity() != null) {
            if (request.preparedQuantity() < 0 || request.preparedQuantity() > oi.getQuantity()) {
                throw new BaseException(ErrorCode.KDS_400_INVALID_QUANTITY);
            }
            ti.setPreparedQuantity(request.preparedQuantity());
        }
        if (request.status() != null && !request.status().isBlank()) {
            PosFlow.Kds target = PosFlow.parseKds(request.status());
            PosFlow.requireKdsTransition(current, target);
            ti.setStatus(target.name());
        } else if (current == PosFlow.Kds.QUEUED) {
            ti.setStatus(PosFlow.Kds.PREPARING.name());
        }
        ticketItemRepository.save(ti);
        // Rollup: all item READY -> ticket READY -> order READY; any PREPARING -> ticket PREPARING.
        List<KdsTicketItem> all = ticketItemRepository.findByKdsTicketIdOrderByCreatedAtAsc(t.getId());
        boolean allReady = all.stream().allMatch(i ->
            PosFlow.parseKds(i.getStatus()) == PosFlow.Kds.READY
                || PosFlow.parseKds(i.getStatus()) == PosFlow.Kds.SERVED);
        boolean anyPreparing = all.stream().anyMatch(i ->
            PosFlow.parseKds(i.getStatus()) == PosFlow.Kds.PREPARING
                || PosFlow.parseKds(i.getStatus()) == PosFlow.Kds.READY);
        if (allReady && PosFlow.parseKds(t.getStatus()) != PosFlow.Kds.READY
            && PosFlow.parseKds(t.getStatus()) != PosFlow.Kds.SERVED) {
            if (PosFlow.parseKds(t.getStatus()) == PosFlow.Kds.QUEUED) {
                transition(t, PosFlow.Kds.PREPARING);
                t.setStartedAt(Instant.now());
            }
            transition(t, PosFlow.Kds.READY);
            t.setReadyAt(Instant.now());
            ticketRepository.save(t);
            syncOrderStatus(t, PosFlow.Order.READY);
        } else if (anyPreparing && PosFlow.parseKds(t.getStatus()) == PosFlow.Kds.QUEUED) {
            transition(t, PosFlow.Kds.PREPARING);
            t.setStartedAt(Instant.now());
            ticketRepository.save(t);
            syncOrderStatus(t, PosFlow.Order.PREPARING);
        }
        return toResponse(ticketRepository.findById(t.getId()).orElseThrow());
    }

    // ---------- helpers ----------

    private int nextQueueNo(UUID branchId) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        Instant dayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        String lockKey = branchId + "|" + today;
        Object lock = queueLocks.computeIfAbsent(lockKey, k -> new Object());
        synchronized (lock) {
            int max = ticketRepository.maxQueueNoToday(branchId, dayStart, dayEnd);
            return max + 1;
        }
    }

    private void transition(KdsTicket t, PosFlow.Kds target) {
        PosFlow.Kds current = PosFlow.parseKds(t.getStatus());
        PosFlow.requireKdsTransition(current, target);
        t.setStatus(target.name());
    }

    private void markItems(UUID ticketId, PosFlow.Kds target) {
        List<KdsTicketItem> items = ticketItemRepository.findByKdsTicketIdOrderByCreatedAtAsc(ticketId);
        for (KdsTicketItem ti : items) {
            PosFlow.Kds current = PosFlow.parseKds(ti.getStatus());
            if (PosFlow.canKdsTransition(current, target)) {
                ti.setStatus(target.name());
                ticketItemRepository.save(ti);
            }
        }
    }

    /**
     * Kéo Order theo bếp (không qua OrderService để tránh vòng lặp bean):
     * CONFIRMED -> PREPARING -> READY. Bỏ qua nếu Order đã đi xa hơn hoặc đã hủy.
     */
    private void syncOrderStatus(KdsTicket t, PosFlow.Order target) {
        Order o = orderRepository.findById(t.getOrderId()).orElse(null);
        if (o == null) {
            return;
        }
        PosFlow.Order current = PosFlow.parseOrder(o.getStatus());
        if (!PosFlow.canOrderTransition(current, target)) {
            return;
        }
        String old = o.getStatus();
        o.setStatus(target.name());
        Instant now = Instant.now();
        if (target == PosFlow.Order.PREPARING) {
            o.setPreparedAt(now);
        } else if (target == PosFlow.Order.READY) {
            o.setReadyAt(now);
        }
        orderRepository.save(o);
        OrderStatusHistory h = new OrderStatusHistory();
        h.setOrderId(o.getId());
        h.setOldStatus(old);
        h.setNewStatus(target.name());
        h.setChangedBy(SecurityUtils.getCurrentPrincipalId().orElse(null));
        h.setChangedAt(now);
        h.setReason("Bếp BAR cập nhật (" + t.getStation() + "-" + String.format("%03d", t.getQueueNo()) + ")");
        h.setStatus("ACTIVE");
        historyRepository.save(h);
    }

    private KdsTicket accessibleTicket(UUID id) {
        KdsTicket t = ticketRepository.findById(id)
            .orElseThrow(() -> new BaseException(ErrorCode.KDS_404_TICKET_NOT_FOUND));
        dataScopeHelper.enforceBranchAccess(t.getBranchId());
        return t;
    }

    private Map<UUID, Order> ordersOf(List<KdsTicket> tickets) {
        List<UUID> orderIds = tickets.stream().map(KdsTicket::getOrderId).distinct().toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return orderRepository.findAllById(orderIds).stream()
            .collect(Collectors.toMap(Order::getId, Function.identity(), (a, b) -> a));
    }

    private Map<UUID, Long> itemCountsOf(List<KdsTicket> tickets) {
        List<UUID> ids = tickets.stream().map(KdsTicket::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return ticketItemRepository.findByKdsTicketIdIn(ids).stream()
            .collect(Collectors.groupingBy(KdsTicketItem::getKdsTicketId, Collectors.counting()));
    }

    private KdsTicketResponse toResponse(KdsTicket t) {
        Order o = orderRepository.findById(t.getOrderId()).orElse(null);
        List<KdsTicketItem> items = ticketItemRepository.findByKdsTicketIdOrderByCreatedAtAsc(t.getId());
        Map<UUID, OrderItem> orderItems = orderItemRepository.findAllById(
            items.stream().map(KdsTicketItem::getOrderItemId).toList()).stream()
            .collect(Collectors.toMap(OrderItem::getId, Function.identity(), (a, b) -> a));
        List<KdsTicketItemResponse> itemResponses = items.stream().map(ti -> {
            OrderItem oi = orderItems.get(ti.getOrderItemId());
            return new KdsTicketItemResponse(ti.getId(), ti.getKdsTicketId(), ti.getOrderItemId(),
                oi == null ? null : oi.getProductCode(), oi == null ? "Món đã xóa" : oi.getProductName(),
                oi == null ? null : oi.getVariantName(), oi == null ? 0 : oi.getQuantity(),
                oi == null ? null : oi.getSugarLevel(), oi == null ? null : oi.getIceLevel(),
                oi == null ? null : oi.getNote(), ti.getPreparedQuantity(), ti.getStatus(), ti.getCreatedAt());
        }).toList();
        return new KdsTicketResponse(t.getId(), t.getOrderId(),
            o == null ? null : o.getOrderCode(), t.getBranchId(), t.getStation(), t.getQueueNo(),
            ticketCode(t.getStation(), t.getQueueNo()), t.getStatus(),
            o == null ? null : o.getCustomerName(), o == null ? null : o.getCustomerPhone(),
            o == null ? null : o.getOrderType(), o == null ? null : o.getNote(),
            t.getStartedAt(), t.getReadyAt(), t.getServedAt(), t.getCreatedAt(), itemResponses);
    }

    private KdsTicketSummaryResponse toSummary(KdsTicket t, Order o, long itemCount) {
        return new KdsTicketSummaryResponse(t.getId(), t.getOrderId(),
            o == null ? null : o.getOrderCode(), t.getBranchId(), t.getStation(), t.getQueueNo(),
            ticketCode(t.getStation(), t.getQueueNo()), t.getStatus(),
            o == null ? null : o.getCustomerName(), o == null ? null : o.getOrderType(),
            (int) itemCount, t.getCreatedAt());
    }

    public static String ticketCode(String station, Integer queueNo) {
        return station + "-" + String.format("%03d", queueNo == null ? 0 : queueNo);
    }

    private void requireViewPermission() {
        if (!SecurityUtils.hasPermission("pos:kds_ticket:view")) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
    }

    private void requireUpdatePermission() {
        if (!SecurityUtils.hasPermission("pos:kds_ticket:update")) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
    }
}
