package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.*;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.OrderService;
import com.erp.backend_service.util.CodeGenerator;
import com.erp.core.domain.*;
import com.erp.core.dto.request.pos.*;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.pos.*;
import com.erp.core.enums.PrincipalType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class OrderServiceImpl implements OrderService {
    private static final String ACTIVE = "ACTIVE";
    private static final BigDecimal DEFAULT_DELIVERY_FEE = BigDecimal.valueOf(15000);
    private final OrderRepository orderRepository;
    private final OrderItemRepository itemRepository;
    private final OrderItemToppingRepository itemToppingRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OrderDeliveryRepository deliveryRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CartItemToppingRepository cartItemToppingRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ToppingRepository toppingRepository;
    private final CustomerRepository customerRepository;
    private final BranchRepository branchRepository;
    private final BranchProductAvailabilityRepository availabilityRepository;
    private final BranchVariantDailyStockRepository stockRepository;
    private final VoucherRepository voucherRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final VoucherBranchRepository voucherBranchRepository;
    private final DataScopeHelper dataScopeHelper;

    public OrderServiceImpl(OrderRepository orderRepository, OrderItemRepository itemRepository,
                            OrderItemToppingRepository itemToppingRepository,
                            OrderStatusHistoryRepository historyRepository,
                            OrderDeliveryRepository deliveryRepository, CartRepository cartRepository,
                            CartItemRepository cartItemRepository, CartItemToppingRepository cartItemToppingRepository,
                            ProductRepository productRepository, ProductVariantRepository variantRepository,
                            ToppingRepository toppingRepository, CustomerRepository customerRepository,
                            BranchRepository branchRepository, BranchProductAvailabilityRepository availabilityRepository,
                            BranchVariantDailyStockRepository stockRepository,
                            VoucherRepository voucherRepository, VoucherUsageRepository voucherUsageRepository,
                            VoucherBranchRepository voucherBranchRepository, DataScopeHelper dataScopeHelper) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.itemToppingRepository = itemToppingRepository;
        this.historyRepository = historyRepository;
        this.deliveryRepository = deliveryRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.cartItemToppingRepository = cartItemToppingRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.toppingRepository = toppingRepository;
        this.customerRepository = customerRepository;
        this.branchRepository = branchRepository;
        this.availabilityRepository = availabilityRepository;
        this.stockRepository = stockRepository;
        this.voucherRepository = voucherRepository;
        this.voucherUsageRepository = voucherUsageRepository;
        this.voucherBranchRepository = voucherBranchRepository;
        this.dataScopeHelper = dataScopeHelper;
    }

    @Override
    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        requireCustomerPermission("pos:order:create");
        UUID customerId = currentCustomerId();
        Branch branch = branchRepository.findById(request.branchId())
                                        .orElseThrow(() -> new BaseException(ErrorCode.INV_404_BRANCH_NOT_FOUND));
        if (!"ACTIVE".equals(branch.getStatus())) {
            throw new BaseException(ErrorCode.INV_400_BRANCH_INACTIVE);
        }
        if ("DELIVERY".equals(request.orderType()) && !branch.isSupportsDelivery()) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "Chi nhánh không hỗ trợ giao hàng.");
        }
        if ("PICKUP".equals(request.orderType()) && !branch.isSupportsPickup()) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "Chi nhánh không hỗ trợ nhận tại cửa hàng.");
        }

        Cart cart = findCart(customerId, request.branchId(), request.sessionToken());
        if (cart == null) {
            throw new BaseException(ErrorCode.ORDER_404_CART_NOT_FOUND);
        }
        List<CartItem> cartItems = cartItemRepository.findByCartIdAndStatusOrderByCreatedAtAsc(cart.getId(), ACTIVE);
        if (cartItems.isEmpty()) {
            throw new BaseException(ErrorCode.ORDER_400_CART_EMPTY);
        }
        if ("DELIVERY".equals(request.orderType()) &&
            (request.shippingAddress() == null || request.shippingAddress().isBlank())) {
            throw new BaseException(ErrorCode.ORDER_400_INVALID_RECEIVER_DATA);
        }
        if ("DELIVERY".equals(request.orderType()) &&
            (request.receiverName() == null || request.receiverName().isBlank() ||
             request.receiverPhone() == null || request.receiverPhone().isBlank())) {
            throw new BaseException(ErrorCode.ORDER_400_INVALID_RECEIVER_DATA);
        }

        Customer customer =
            customerRepository.findById(customerId).orElseThrow(() -> new BaseException(ErrorCode.CUSTOMER_NOT_FOUND));
        BigDecimal subtotal = BigDecimal.ZERO;
        List<CartItem> checked = new ArrayList<>();
        for (CartItem ci : cartItems) {
            Product p = productRepository.findById(ci.getProductId())
                                         .orElseThrow(() -> new BaseException(ErrorCode.ORDER_404_PRODUCT_NOT_FOUND));
            if (!"ACTIVE".equals(p.getStatus())) {
                throw new BaseException(ErrorCode.ORDER_404_PRODUCT_NOT_FOUND);
            }
            availabilityRepository.findByBranchIdAndProductIdAndStatus(request.branchId(), p.getId(), ACTIVE)
                                  .filter(BranchProductAvailability::isAvailable)
                                  .orElseThrow(() -> new BaseException(ErrorCode.ORDER_404_PRODUCT_NOT_FOUND));
            if (ci.getVariantId() != null) {
                ProductVariant variant = variantRepository.findByIdAndProductId(ci.getVariantId(), p.getId()).orElseThrow(
                    () -> new BaseException(ErrorCode.INVALID_REQUEST, "Biến thể sản phẩm không hợp lệ."));
                if (!ACTIVE.equals(variant.getStatus())) {
                    throw new BaseException(ErrorCode.INVALID_REQUEST, "Biến thể sản phẩm không còn khả dụng.");
                }
                checkStock(request.branchId(), ci.getVariantId(), ci.getQuantity());
            }
            subtotal = subtotal.add(ci.getTotalPrice());
            checked.add(ci);
        }

        BigDecimal discount = BigDecimal.ZERO;
        Voucher voucher = null;
        if (request.voucherCode() != null && !request.voucherCode().isBlank()) {
            voucher = voucherRepository.findByCodeIgnoreCaseAndStatus(request.voucherCode().trim(), ACTIVE).orElseThrow(
                () -> new BaseException(ErrorCode.INVALID_REQUEST, "Voucher không hợp lệ."));
            if (voucherBranchRepository.findByVoucherIdAndBranchIdAndStatus(voucher.getId(), request.branchId(), ACTIVE)
                                        .isEmpty()) {
                throw new BaseException(ErrorCode.INVALID_REQUEST, "Voucher không áp dụng tại chi nhánh này.");
            }
            Instant now = Instant.now();
            if (now.isBefore(voucher.getStartAt()) || now.isAfter(voucher.getEndAt())) {
                throw new BaseException(ErrorCode.INVALID_REQUEST, "Voucher đã hết hạn hoặc chưa bắt đầu.");
            }
            if (subtotal.compareTo(voucher.getMinOrderAmount()) < 0) {
                throw new BaseException(ErrorCode.INVALID_REQUEST, "Đơn hàng chưa đạt giá trị tối thiểu của voucher.");
            }
            if (voucher.getUsageLimit() != null && voucher.getUsedCount() >= voucher.getUsageLimit()) {
                throw new BaseException(ErrorCode.INVALID_REQUEST, "Voucher đã hết lượt sử dụng.");
            }
            if (voucher.getUsageLimitPerCustomer() != null &&
                voucherUsageRepository.countByVoucherIdAndCustomerIdAndStatus(voucher.getId(), customerId, ACTIVE) >=
                    voucher.getUsageLimitPerCustomer()) {
                throw new BaseException(ErrorCode.INVALID_REQUEST, "Bạn đã sử dụng voucher quá số lần cho phép.");
            }
            discount = "PERCENT".equalsIgnoreCase(voucher.getDiscountType()) ?
                subtotal.multiply(voucher.getDiscountValue()).divide(BigDecimal.valueOf(100)) :
                voucher.getDiscountValue();
            if (voucher.getMaxDiscountAmount() != null) {
                discount = discount.min(voucher.getMaxDiscountAmount());
            }
            discount = discount.min(subtotal).max(BigDecimal.ZERO);
        }
        BigDecimal fee = "DELIVERY".equals(request.orderType()) ? DEFAULT_DELIVERY_FEE : BigDecimal.ZERO;
        BigDecimal total = subtotal.subtract(discount).add(fee).max(BigDecimal.ZERO);

        Order o = new Order();
        o.setOrderCode(generateOrderCode());
        o.setBranchId(branch.getId());
        o.setCustomerId(customerId);
        o.setCustomerName(request.receiverName() != null ? request.receiverName().trim() : customer.getFullName());
        o.setCustomerPhone(request.receiverPhone() != null ? request.receiverPhone().trim() : customer.getPhone());
        o.setCustomerEmail(customer.getEmail());
        o.setOrderType(request.orderType());
        o.setStatus("PENDING");
        o.setPaymentMethod(request.paymentMethod());
        o.setPaymentStatus("UNPAID");
        o.setSubtotalAmount(subtotal);
        o.setDiscountAmount(discount);
        o.setDeliveryFee(fee);
        o.setTotalAmount(total);
        o.setTotalCogsAmount(BigDecimal.ZERO);
        o.setDeliveryAddress(request.shippingAddress());
        o.setNote(request.note());
        o = orderRepository.save(o);

        for (CartItem ci : checked) {
            Product p = productRepository.findById(ci.getProductId()).orElseThrow();
            ProductVariant v =
                ci.getVariantId() == null ? null : variantRepository.findById(ci.getVariantId()).orElse(null);
            OrderItem oi = new OrderItem();
            oi.setOrderId(o.getId());
            oi.setProductId(p.getId());
            oi.setVariantId(ci.getVariantId());
            oi.setProductCode(p.getCode());
            oi.setProductName(p.getName());
            oi.setVariantName(v == null ? null : v.getVariantName());
            oi.setQuantity(ci.getQuantity());
            oi.setSugarLevel(ci.getSugarLevel());
            oi.setIceLevel(ci.getIceLevel());
            oi.setNote(ci.getNote());
            oi.setUnitPrice(ci.getUnitPrice());
            oi.setTotalPrice(ci.getTotalPrice());
            oi.setUnitCogsAmount(BigDecimal.ZERO);
            oi.setStatus(ACTIVE);
            oi = itemRepository.save(oi);
            for (CartItemTopping ct : cartItemToppingRepository.findByCartItemIdAndStatus(ci.getId(), ACTIVE)) {
                Topping t = toppingRepository.findById(ct.getToppingId()).orElseThrow();
                OrderItemTopping ot = new OrderItemTopping();
                ot.setOrderItemId(oi.getId());
                ot.setToppingId(t.getId());
                ot.setToppingCode(t.getCode());
                ot.setToppingName(t.getName());
                ot.setQuantity(ct.getQuantity());
                ot.setUnitPrice(ct.getUnitPrice());
                ot.setTotalPrice(ct.getTotalPrice());
                ot.setStatus(ACTIVE);
                itemToppingRepository.save(ot);
            }
        }
        writeHistory(o, null, "PENDING", "Khởi tạo đơn hàng từ Cart");
        if (voucher != null) {
            VoucherUsage vu = new VoucherUsage();
            vu.setVoucherId(voucher.getId());
            vu.setOrderId(o.getId());
            vu.setCustomerId(customerId);
            vu.setDiscountAmount(discount);
            vu.setUsedAt(Instant.now());
            vu.setStatus(ACTIVE);
            voucherUsageRepository.save(vu);
            voucher.setUsedCount(voucher.getUsedCount() + 1);
            voucherRepository.save(voucher);
        }
        if ("DELIVERY".equals(o.getOrderType())) {
            OrderDelivery d = new OrderDelivery();
            d.setOrderId(o.getId());
            d.setReceiverName(o.getCustomerName());
            d.setReceiverPhone(o.getCustomerPhone());
            d.setDeliveryAddress(o.getDeliveryAddress());
            d.setDeliveryFee(fee);
            d.setStatus("PENDING");
            deliveryRepository.save(d);
        }
        cartItemToppingRepository.deleteAll(cartItems.stream().flatMap(
            i -> cartItemToppingRepository.findByCartItemIdAndStatus(i.getId(), ACTIVE).stream()).toList());
        cartItemRepository.deleteAll(cartItems);
        cart.setStatus("CONVERTED");
        cart.setSubtotalAmount(BigDecimal.ZERO);
        cartRepository.save(cart);
        return toResponse(o);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> list(UUID branchId, String orderType, String status, LocalDate fromDate,
                                                   LocalDate toDate, int page, int size) {
        requirePermission("pos:order:view");
        if (page < 0 || size < 1 || size > 100) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "page/size không hợp lệ.");
        }
        UUID customerId = null;
        UUID effectiveBranch = branchId;
        if (isCustomer()) {
            customerId = currentPrincipalId();
        } else {
            effectiveBranch = dataScopeHelper.resolveEffectiveBranchId(branchId);
        }
        Instant from = fromDate == null ? null : fromDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant to = toDate == null ? null : toDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        Page<Order> p = orderRepository.search(effectiveBranch, customerId, orderType, status, from, to,
                                               PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResponse<>(p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(),
                                  p.getContent().stream().map(
                                      o -> new OrderSummaryResponse(o.getId(), o.getOrderCode(), o.getBranchId(),
                                                                    o.getOrderType(), o.getCustomerName(),
                                                                    o.getTotalAmount(), o.getStatus(),
                                                                    o.getCreatedAt())).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        requirePermission("pos:order:view");
        Order o = findAccessible(id);
        return toResponse(o);
    }

    @Override
    @Transactional
    public OrderStatusResponse updateStatus(UUID id, UpdateOrderStatusRequest request) {
        requirePermission("pos:order:update");
        Order o = findAccessible(id);
        String target = request.status().trim().toUpperCase(Locale.ROOT);
        if (!validTransition(o.getStatus(), target)) {
            throw new BaseException(ErrorCode.ORDER_400_INVALID_STATUS_TRANSITION);
        }
        if ("COMPLETED".equals(target) && !"PAID".equalsIgnoreCase(o.getPaymentStatus())) {
            throw new BaseException(ErrorCode.ORDER_400_UNPAID);
        }
        String old = o.getStatus();
        o.setStatus(target);
        Instant now = Instant.now();
        switch (target) {
            case "CONFIRMED" -> o.setConfirmedAt(now);
            case "PREPARING" -> o.setPreparedAt(now);
            case "READY" -> o.setReadyAt(now);
            case "DELIVERING" -> o.setDeliveringAt(now);
            case "COMPLETED" -> {
                o.setCompletedAt(now);
                deductStock(o);
            }
            case "CANCELLED" -> o.setCancelledAt(now);
            case "REJECTED" -> o.setRejectedAt(now);
            default -> {
            }
        }
        orderRepository.save(o);
        UUID by = currentPrincipalId();
        writeHistory(o, old, target, request.note());
        return new OrderStatusResponse(o.getId(), o.getOrderCode(), old, target, by, now);
    }

    @Override
    @Transactional
    public OrderResponse cancel(UUID id, CancelOrderRequest request) {
        requirePermission("pos:order:cancel");
        Order o = findAccessible(id);
        if (!Set.of("PENDING", "CONFIRMED", "PREPARING").contains(o.getStatus())) {
            throw new BaseException(ErrorCode.ORDER_400_ORDER_NOT_CANCELLABLE);
        }
        String old = o.getStatus();
        o.setStatus("CANCELLED");
        o.setCancelReason(request.reason());
        o.setCancelledAt(Instant.now());
        orderRepository.save(o);
        restoreVoucher(o);
        writeHistory(o, old, "CANCELLED", request.note() != null ? request.note() : request.reason());
        return toResponse(o);
    }

    @Override
    @Transactional
    public OrderResponse complete(UUID id, CompleteOrderRequest request) {
        requirePermission("pos:order:update");
        Order o = findAccessible(id);
        if (!"PAID".equalsIgnoreCase(o.getPaymentStatus())) {
            throw new BaseException(ErrorCode.ORDER_400_UNPAID);
        }
        if (!Set.of("READY", "DELIVERING").contains(o.getStatus())) {
            throw new BaseException(ErrorCode.ORDER_400_INVALID_STATUS_TRANSITION);
        }
        String old = o.getStatus();
        Instant now = Instant.now();
        o.setStatus("COMPLETED");
        o.setCompletedAt(now);
        orderRepository.save(o);
        for (OrderItem oi : itemRepository.findByOrderIdAndStatusOrderByCreatedAtAsc(o.getId(), ACTIVE)) {
            if (oi.getVariantId() != null) {
                BranchVariantDailyStock s =
                    stockRepository.findByBranchIdAndVariantIdAndBusinessDateAndStatus(o.getBranchId(),
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
        writeHistory(o, old, "COMPLETED", request.note());
        return toResponse(o);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderHistoryResponse> history(UUID id) {
        requirePermission("pos:order:view");
        findAccessible(id);
        return historyRepository.findByOrderIdOrderByChangedAtAsc(id).stream().map(
            h -> new OrderHistoryResponse(h.getId(), h.getOrderId(), h.getOldStatus(), h.getNewStatus(),
                                          h.getChangedBy(), h.getChangedAt(), h.getReason())).toList();
    }

    private Order findAccessible(UUID id) {
        Order o =
            orderRepository.findById(id).orElseThrow(() -> new BaseException(ErrorCode.ORDER_404_ORDER_NOT_FOUND));
        if (isCustomer()) {
            if (!Objects.equals(o.getCustomerId(), currentPrincipalId())) {
                throw new BaseException(ErrorCode.CROSS_SCOPE_DENIED);
            }
        } else {
            dataScopeHelper.enforceBranchAccess(o.getBranchId());
        }
        return o;
    }

    private Cart findCart(UUID customerId, UUID branchId, String token) {
        return cartRepository.findByCustomerIdAndBranchIdAndStatus(customerId, branchId, ACTIVE).orElseGet(
            () -> token == null ? null :
                cartRepository.findBySessionTokenAndBranchIdAndStatus(token, branchId, ACTIVE).orElse(null));
    }

    private void writeHistory(Order o, String old, String target, String reason) {
        OrderStatusHistory h = new OrderStatusHistory();
        h.setOrderId(o.getId());
        h.setOldStatus(old);
        h.setNewStatus(target);
        h.setChangedBy(isCustomer() ? null : currentPrincipalId());
        h.setChangedAt(Instant.now());
        h.setReason(reason);
        h.setStatus(ACTIVE);
        historyRepository.save(h);
    }

    private void restoreVoucher(Order o) {
        voucherUsageRepository.findByOrderIdAndStatus(o.getId(), ACTIVE).ifPresent(vu -> {
            vu.setStatus("CANCELLED");
            voucherUsageRepository.save(vu);
            voucherRepository.findById(vu.getVoucherId()).ifPresent(v -> {
                v.setUsedCount(Math.max(0, v.getUsedCount() - 1));
                voucherRepository.save(v);
            });
        });
    }

    private boolean validTransition(String a, String b) {
        return (a.equals("PENDING") && b.equals("CONFIRMED")) ||
            (a.equals("CONFIRMED") && b.equals("PREPARING")) ||
            (a.equals("PREPARING") && b.equals("READY")) ||
            (a.equals("READY") && b.equals("DELIVERING")) ||
            (a.equals("DELIVERING") && b.equals("COMPLETED")) ||
            (Set.of("PENDING", "CONFIRMED", "PREPARING").contains(a) && b.equals("CANCELLED")) ||
            (Set.of("PENDING", "CONFIRMED", "PREPARING").contains(a) && b.equals("REJECTED"));
    }

    private void checkStock(UUID branch, UUID variant, int qty) {
        BranchVariantDailyStock s =
            stockRepository.findByBranchIdAndVariantIdAndBusinessDateAndStatus(branch, variant, LocalDate.now(), ACTIVE)
                           .orElseThrow(() -> new BaseException(ErrorCode.ORDER_400_INVALID_QUANTITY));
        if (qty > s.getRemainingQuantity()) {
            throw new BaseException(ErrorCode.ORDER_400_INVALID_QUANTITY);
        }
    }

    private void deductStock(Order order) {
        for (OrderItem oi : itemRepository.findByOrderIdAndStatusOrderByCreatedAtAsc(order.getId(), ACTIVE)) {
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

    private String generateOrderCode() {
        return CodeGenerator.nextDailySequence("HD-",
            prefix -> orderRepository.findTopByOrderCodeStartsWithOrderByOrderCodeDesc(prefix)
                                     .map(Order::getOrderCode),
            orderRepository::existsByOrderCode);
    }

    private OrderResponse toResponse(Order o) {
        List<OrderItemResponse> items =
            itemRepository.findByOrderIdAndStatusOrderByCreatedAtAsc(o.getId(), ACTIVE).stream().map(i -> {
                List<OrderItemToppingResponse> tops =
                    itemToppingRepository.findByOrderItemIdAndStatus(i.getId(), ACTIVE).stream().map(
                        t -> new OrderItemToppingResponse(t.getToppingId(), t.getToppingName(), t.getQuantity(),
                                                          t.getUnitPrice(), t.getTotalPrice())).toList();
                ProductVariant variant = i.getVariantId() == null ? null : variantRepository.findById(i.getVariantId()).orElse(null);
                return new OrderItemResponse(i.getId(), i.getProductCode(), i.getProductName(), i.getVariantId(),
                                             variant == null ? null : variant.getVariantCode(), i.getVariantName(),
                                             i.getQuantity(), i.getIceLevel(), i.getSugarLevel(),
                                             i.getNote(), i.getUnitPrice(), i.getTotalPrice(), i.getUnitCogsAmount(),
                                             tops);
            }).toList();
        OrderDelivery d = deliveryRepository.findByOrderId(o.getId()).orElse(null);
        DeliveryResponse dr = d == null ? null :
            new DeliveryResponse(d.getId(), d.getOrderId(), d.getShipperId(), d.getReceiverName(), d.getReceiverPhone(),
                                 d.getDeliveryAddress(), d.getDeliveryNote(), d.getDeliveryFee(), d.getStatus(),
                                 d.getAssignedAt(), d.getPickedUpAt(), d.getDeliveredAt(), d.getFailedAt(),
                                 d.getFailReason());
        return new OrderResponse(o.getId(), o.getOrderCode(), o.getBranchId(), o.getCustomerId(), o.getCustomerName(),
                                 o.getCustomerPhone(), o.getCustomerEmail(), o.getOrderType(), o.getStatus(),
                                 o.getPaymentMethod(), o.getPaymentStatus(), o.getSubtotalAmount(),
                                 o.getDiscountAmount(), o.getDeliveryFee(), o.getTotalAmount(), o.getTotalCogsAmount(),
                                 o.getDeliveryAddress(), o.getNote(), o.getCreatedAt(), items, dr);
    }

    private void requireCustomerPermission(String permission) {
        if (isCustomer()) {
            return;
        }
        if (!SecurityUtils.hasPermission(permission) && !SecurityUtils.hasPermission(permission.replace("pos:", ""))) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
    }

    private void requirePermission(String permission) {
        if (isCustomer()) {
            if (!"pos:order:view".equals(permission) && !"pos:order:cancel".equals(permission)) {
                throw new BaseException(ErrorCode.UNAUTHORIZED);
            }
            return;
        }
        if (!SecurityUtils.hasPermission(permission) && !SecurityUtils.hasPermission(permission.replace("pos:", ""))) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
    }

    private boolean isCustomer() {
        return SecurityUtils.getCurrentPrincipalType().orElse(null) == PrincipalType.CUSTOMER;
    }

    private UUID currentPrincipalId() {
        return SecurityUtils.getCurrentPrincipalId().orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));
    }

    private UUID currentCustomerId() {
        if (!isCustomer()) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Chỉ CUSTOMER được tạo đơn từ Cart.");
        }
        return currentPrincipalId();
    }
}
