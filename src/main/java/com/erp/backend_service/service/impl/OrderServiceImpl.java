package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.*;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.OrderService;
import com.erp.backend_service.service.pos.PosCogsService;
import com.erp.backend_service.service.pos.PosComboService;
import com.erp.backend_service.service.pos.PosBranchOpenService;
import com.erp.backend_service.service.pos.PosFlow;
import com.erp.backend_service.service.pos.PosIdempotencyService;
import com.erp.backend_service.service.pos.PosShipperAssignService;
import com.erp.backend_service.util.CodeGenerator;
import com.erp.core.domain.*;
import com.erp.core.dto.request.pos.*;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.pos.*;
import com.erp.core.enums.PrincipalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.backend_service.event.OrderRealtimeEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Service
public class OrderServiceImpl implements OrderService {
    private static final String ACTIVE = "ACTIVE";
    private static final BigDecimal DEFAULT_DELIVERY_FEE = BigDecimal.valueOf(15000);
    private static final String VOUCHER_INVALID_MSG = "Mã giảm giá không hợp lệ hoặc không áp dụng cho đơn hàng này.";
    /** Quyền CUSTOMER được phép trong luồng đọc/hủy đơn của chính mình. */
    private static final Set<String> CUSTOMER_ALLOWED_PERMISSIONS = Set.of(
        "pos:order:view",
        "pos:order:cancel"
    );
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
    private final VoucherRepository voucherRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final VoucherBranchRepository voucherBranchRepository;
    private final DataScopeHelper dataScopeHelper;
    private final PosComboService posComboService;
    private final PosCogsService posCogsService;
    private final RefundRepository refundRepository;
    private final PosIdempotencyService posIdempotencyService;
    private final PosBranchOpenService posBranchOpenService;
    private final PosShipperAssignService posShipperAssignService;
    private final PickupTimeSlotRepository pickupTimeSlotRepository;
    private final ApplicationEventPublisher eventPublisher;

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    public OrderServiceImpl(OrderRepository orderRepository, OrderItemRepository itemRepository,
                            OrderItemToppingRepository itemToppingRepository,
                            OrderStatusHistoryRepository historyRepository,
                            OrderDeliveryRepository deliveryRepository, CartRepository cartRepository,
                            CartItemRepository cartItemRepository, CartItemToppingRepository cartItemToppingRepository,
                            ProductRepository productRepository, ProductVariantRepository variantRepository,
                            ToppingRepository toppingRepository, CustomerRepository customerRepository,
                            BranchRepository branchRepository, BranchProductAvailabilityRepository availabilityRepository,
                            VoucherRepository voucherRepository, VoucherUsageRepository voucherUsageRepository,
                            VoucherBranchRepository voucherBranchRepository, DataScopeHelper dataScopeHelper,
                            PosComboService posComboService, PosCogsService posCogsService,
                            RefundRepository refundRepository, PosIdempotencyService posIdempotencyService,
                            PosBranchOpenService posBranchOpenService,
                            PosShipperAssignService posShipperAssignService,
                            PickupTimeSlotRepository pickupTimeSlotRepository, ApplicationEventPublisher eventPublisher) {
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
        this.voucherRepository = voucherRepository;
        this.voucherUsageRepository = voucherUsageRepository;
        this.voucherBranchRepository = voucherBranchRepository;
        this.dataScopeHelper = dataScopeHelper;
        this.posComboService = posComboService;
        this.posCogsService = posCogsService;
        this.refundRepository = refundRepository;
        this.posIdempotencyService = posIdempotencyService;
        this.posBranchOpenService = posBranchOpenService;
        this.posShipperAssignService = posShipperAssignService;
        this.pickupTimeSlotRepository = pickupTimeSlotRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public OrderResponse create(String idempotencyKey, CreateOrderRequest request) {
        requireCustomerPermission("pos:order:create");
        UUID customerId = currentCustomerId();
        log.info("Create order: branch={}, customer={}, orderType={}", request.branchId(), customerId,
            request.orderType());
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return createInternal(customerId, request);
        }
        String key = idempotencyKey.trim();
        String hash = posIdempotencyService.hash(customerId, canonicalRequest(request));
        // Retry cùng key+hash -> trả đơn cũ, không tạo mới, không trừ voucher/kho lần 2.
        Optional<UUID> replayed = posIdempotencyService.replayOrderId(key, hash);
        if (replayed.isPresent()) {
            Order existing =
                orderRepository.findById(replayed.get())
                    .orElseThrow(() -> new BaseException(ErrorCode.ORDER_404_ORDER_NOT_FOUND));
            if (!customerId.equals(existing.getCustomerId())) {
                throw new BaseException(ErrorCode.CROSS_SCOPE_DENIED);
            }
            return toResponse(existing);
        }
        IdempotencyKey record = posIdempotencyService.claim(key, hash);
        try {
            OrderResponse response = createInternal(customerId, request);
            posIdempotencyService.succeeded(record, response.id());
            return response;
        } catch (RuntimeException e) {
            posIdempotencyService.failed(record);
            throw e;
        }
    }

    private String canonicalRequest(CreateOrderRequest request) {
        return String.join("|", String.valueOf(request.branchId()), String.valueOf(request.orderType()),
            String.valueOf(request.voucherCode()), String.valueOf(request.receiverName()),
            String.valueOf(request.receiverPhone()), String.valueOf(request.shippingAddress()),
            String.valueOf(request.paymentMethod()), String.valueOf(request.note()),
            String.valueOf(request.pickupTimeSlotId()),
            String.valueOf(request.sessionToken()));
    }

    private OrderResponse createInternal(UUID customerId, CreateOrderRequest request) {
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
            }
            // Giả thiết B1: subtotal phải khớp Cart (item + topping). Giả thiết combo A4 tái validate ở chốt đơn.
            posComboService.validateForSale(ci.getProductId(), ci.getVariantId(), ci.getQuantity(),
                request.branchId());
            subtotal = subtotal.add(ci.getTotalPrice());
            BigDecimal toppingsTotal = cartItemToppingRepository.findByCartItemIdAndStatus(ci.getId(), ACTIVE)
                .stream()
                .map(CartItemTopping::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            subtotal = subtotal.add(toppingsTotal);
            checked.add(ci);
        }

        BigDecimal discount = BigDecimal.ZERO;
        Voucher voucher = null;
        if (request.voucherCode() != null && !request.voucherCode().isBlank()) {
            // Check rẻ (chi nhánh, hạn, giá trị tối thiểu) đọc không lock để khỏi giữ lock lâu.
            Voucher preview =
                voucherRepository.findByCodeIgnoreCaseAndStatus(request.voucherCode().trim(), ACTIVE).orElseThrow(
                    () -> new BaseException(ErrorCode.INVALID_REQUEST, VOUCHER_INVALID_MSG));
            if (voucherBranchRepository.findByVoucherIdAndBranchIdAndStatus(preview.getId(), request.branchId(),
                ACTIVE).isEmpty()) {
                throw new BaseException(ErrorCode.INVALID_REQUEST, VOUCHER_INVALID_MSG);
            }
            Instant now = Instant.now();
            if (now.isBefore(preview.getStartAt()) || now.isAfter(preview.getEndAt())) {
                throw new BaseException(ErrorCode.INVALID_REQUEST, VOUCHER_INVALID_MSG);
            }
            if (subtotal.compareTo(preview.getMinOrderAmount()) < 0) {
                throw new BaseException(ErrorCode.INVALID_REQUEST, VOUCHER_INVALID_MSG);
            }
            // Lock bi quan row voucher: check hạn mức + ghi usage + tăng usedCount thành 1 khối,
            // 2 đơn cùng lúc không thể cùng lọt (kể cả limit-theo-khách vì count nằm trong lock).
            voucher = voucherRepository.findByIdForUpdate(preview.getId()).orElseThrow(
                () -> new BaseException(ErrorCode.INVALID_REQUEST, "Voucher không hợp lệ."));
            if (voucher.getUsageLimit() != null && voucher.getUsedCount() >= voucher.getUsageLimit()) {
                throw new BaseException(ErrorCode.INVALID_REQUEST, "Voucher đã hết lượt sử dụng.");
            }
            if (voucher.getUsageLimitPerCustomer() != null &&
                voucherUsageRepository.countByVoucherIdAndCustomerIdAndStatus(voucher.getId(), customerId, ACTIVE) >=
                    voucher.getUsageLimitPerCustomer()) {
                throw new BaseException(ErrorCode.INVALID_REQUEST, "Bạn đã sử dụng voucher quá số lần cho phép.");
            }
            // Giả thiết B2: PERCENT làm tròn HALF_UP 2 decimals để không crash số lẻ.
            discount = "PERCENT".equalsIgnoreCase(voucher.getDiscountType()) ?
                subtotal.multiply(voucher.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP) :
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

        // Kiểm soát khung giờ pickup & chống quá tải (BR-ORG-04 & Test Case 3)
        if ("PICKUP".equals(request.orderType()) && request.pickupTimeSlotId() != null) {
            PickupTimeSlot slot = pickupTimeSlotRepository.findByIdForUpdate(request.pickupTimeSlotId())
                .orElseThrow(() -> new BaseException(ErrorCode.PICKUP_SLOT_404_NOT_FOUND));
            if (!branch.getId().equals(slot.getBranchId()) || !"ACTIVE".equalsIgnoreCase(slot.getStatus())) {
                throw new BaseException(ErrorCode.INVALID_REQUEST, "Khung giờ pickup không khả dụng tại chi nhánh này.");
            }
            ZoneId zone;
            try {
                zone = ZoneId.of(branch.getTimezone() != null ? branch.getTimezone() : "Asia/Ho_Chi_Minh");
            } catch (Exception e) {
                zone = ZoneId.of("Asia/Ho_Chi_Minh");
            }
            ZonedDateTime now = ZonedDateTime.now(zone);
            Instant startOfDay = now.toLocalDate().atStartOfDay(zone).toInstant();
            Instant endOfDay = now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();

            long activeInSlot = orderRepository.countActiveOrdersInSlotOnDate(branch.getId(), slot.getId(), startOfDay, endOfDay);
            if (slot.getMaxOrders() != null && activeInSlot >= slot.getMaxOrders()) {
                throw new BaseException(ErrorCode.ORDER_400_SLOT_FULL);
            }
            o.setPickupTimeSlotId(slot.getId());
            o.setPickupTime(now.toLocalDate().atTime(slot.getStartTime()).atZone(zone).toInstant());
        }

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
            // Giả thiết B3: COGS từ BOM × giá NCC ưu tiên, thiếu thì ZERO (không chặn bán).
            BigDecimal unitCogs = posCogsService.unitCogs(ci.getVariantId());
            oi.setUnitCogsAmount(unitCogs);
            oi.setStatus(ACTIVE);
            oi = itemRepository.save(oi);
            o.setTotalCogsAmount(o.getTotalCogsAmount().add(unitCogs.multiply(BigDecimal.valueOf(ci.getQuantity()))));
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
        // Tạo delivery record TRƯỚC auto-confirm để auto-assign tìm thấy (bug cũ:
        // record tạo sau nên autoAssign luôn thấy trống và bỏ qua).
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
        // Tự động CONFIRMED khi đủ điều kiện (tiền mặt/COD + chi nhánh mở cửa):
        // quán không phải bấm tay từng đơn, trang quản lý chỉ xử lý đơn kẹt.
        // Online (VNPAY/MOMO/BANK_TRANSFER) ở lại PENDING chờ thanh toán thật.
        if (isAutoConfirmable(o)) {
            String confirmedOld = o.getStatus();
            o.setStatus(PosFlow.Order.CONFIRMED.name());
            o.setConfirmedAt(Instant.now());
            orderRepository.save(o);
            reserveAll(o);
            posShipperAssignService.autoAssign(o);
            writeHistory(o, confirmedOld, PosFlow.Order.CONFIRMED.name(),
                "Tự động xác nhận: chi nhánh mở cửa và thanh toán tiền mặt/COD");
        }
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
        cartItemToppingRepository.deleteAll(cartItems.stream().flatMap(
            i -> cartItemToppingRepository.findByCartItemIdAndStatus(i.getId(), ACTIVE).stream()).toList());
        cartItemRepository.deleteAll(cartItems);
        cart.setStatus("CONVERTED");
        cart.setSubtotalAmount(BigDecimal.ZERO);
        cartRepository.save(cart);
        publishRealtimeEvent(o, OrderRealtimeEvent.TYPE_ORDER_CREATED,
            "Đơn hàng mới: #" + o.getOrderCode(),
            "Đơn hàng mới từ " + (o.getCustomerName() != null ? o.getCustomerName() : "Khách hàng") + " (" + o.getTotalAmount() + "đ)");
        log.info("Order created: id={}, code={}, status={}, total={}", o.getId(), o.getOrderCode(), o.getStatus(),
            o.getTotalAmount());
        return toResponse(o);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> list(UUID branchId, String orderType, String status, LocalDate fromDate,
                                                   LocalDate toDate, String search, int page, int size) {
        log.info("List orders: orderType={}, status={}, page={}, size={}", orderType, status, page, size);
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
        Page<Order> p = orderRepository.findAll(
            OrderSpecifications.filter(effectiveBranch, customerId, orderType, status, from, to, search),
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
        log.info("Get order id={}", id);
        requirePermission("pos:order:view");
        Order o = findAccessible(id);
        return toResponse(o);
    }

    @Override
    @Transactional
    public OrderStatusResponse updateStatus(UUID id, UpdateOrderStatusRequest request) {
        log.info("Update status order id={}, target={}", id, request.status());
        requirePermission("pos:order:update");
        Order o = findAccessible(id);
        PosFlow.Order target = PosFlow.parseOrder(request.status());
        PosFlow.Order current = PosFlow.parseOrder(o.getStatus());
        PosFlow.requireOrderTransition(current, target);
        if (target == PosFlow.Order.COMPLETED) {
            if (!"PAID".equalsIgnoreCase(o.getPaymentStatus())) {
                throw new BaseException(ErrorCode.ORDER_400_UNPAID);
            }
            // Hoàn tất đúng chặng theo loại đơn (giống complete()): PICKUP từ READY,
            // DELIVERY từ DELIVERING. Cấm DELIVERY nhảy READY -> COMPLETED bỏ qua giao hàng.
            boolean pickupReady = "PICKUP".equals(o.getOrderType()) && current == PosFlow.Order.READY;
            boolean deliveryDelivering =
                "DELIVERY".equals(o.getOrderType()) && current == PosFlow.Order.DELIVERING;
            if (!(pickupReady || deliveryDelivering)) {
                throw new BaseException(ErrorCode.ORDER_400_INVALID_STATUS_TRANSITION);
            }
        }
        String old = o.getStatus();
        o.setStatus(target.name());
        Instant now = Instant.now();
        switch (target) {
            case CONFIRMED -> {
                o.setConfirmedAt(now);
                // Giả thiết C1: reserve tồn ngay khi quán nhận đơn (lock + log), khỏi oversell.
                reserveAll(o);
                // Đơn giao: thử gán shipper rảnh nhất luôn, không có xe thì chờ gán tay.
                posShipperAssignService.autoAssign(o);
            }
            case PREPARING -> o.setPreparedAt(now);
            case READY -> o.setReadyAt(now);
            case DELIVERING -> o.setDeliveringAt(now);
            case COMPLETED -> {
                // Đã reserve ở CONFIRMED nên không trừ lần 2 (lỗi ẩn double-deduct cũ).
                o.setCompletedAt(now);
            }
            case CANCELLED -> {
                o.setCancelledAt(now);
                restoreVoucher(o);
                // PENDING chưa reserve nên không hoàn (hoàn thừa sẽ phình tồn).
                if (current != PosFlow.Order.PENDING) {
                    releaseAll(o);
                }
                cancelDelivery(o);
            }
            case REJECTED -> {
                o.setRejectedAt(now);
                restoreVoucher(o);
                if (current != PosFlow.Order.PENDING) {
                    releaseAll(o);
                }
                cancelDelivery(o);
            }
            default -> {
            }
        }
        orderRepository.save(o);
        UUID by = currentPrincipalId();
        writeHistory(o, old, target.name(), request.note());
        publishRealtimeEvent(o, OrderRealtimeEvent.TYPE_ORDER_STATUS_CHANGED,
            "Cập nhật đơn hàng: #" + o.getOrderCode(),
            "Đơn hàng #" + o.getOrderCode() + " đã chuyển sang trạng thái " + target.name());
        log.info("Order status changed: code={}, {} -> {}", o.getOrderCode(), old, target.name());
        return new OrderStatusResponse(o.getId(), o.getOrderCode(), old, target.name(), by, now);
    }

    @Override
    @Transactional
    public OrderResponse cancel(UUID id, CancelOrderRequest request) {
        log.info("Cancel order id={}, reason={}", id, request.reason());
        requirePermission("pos:order:cancel");
        Order o = findAccessible(id);
        PosFlow.Order current = PosFlow.parseOrder(o.getStatus());
        if (!(current == PosFlow.Order.PENDING || current == PosFlow.Order.CONFIRMED ||
            current == PosFlow.Order.PREPARING)) {
            throw new BaseException(ErrorCode.ORDER_400_ORDER_NOT_CANCELLABLE);
        }
        String old = o.getStatus();
        o.setStatus(PosFlow.Order.CANCELLED.name());
        o.setCancelReason(request.reason());
        o.setCancelledAt(Instant.now());
        orderRepository.save(o);
        restoreVoucher(o);
        // Chỉ hoàn tồn nếu đơn đã từng reserve (CONFIRMED trở đi); PENDING thì chưa trừ.
        if (current != PosFlow.Order.PENDING) {
            releaseAll(o);
        }
        cancelDelivery(o);
        // Giả thiết D1: hủy đơn đã PAID phải sinh refund PENDING cho kế toán, tránh mất tiền khách.
        refundIfPaid(o, request.reason());
        writeHistory(o, old, PosFlow.Order.CANCELLED.name(), request.note() != null ? request.note() : request.reason());
        publishRealtimeEvent(o, OrderRealtimeEvent.TYPE_ORDER_STATUS_CHANGED,
            "Đơn hàng #" + o.getOrderCode() + " đã bị hủy",
            "Lý do: " + (request.reason() != null ? request.reason() : "Khách hủy"));
        log.info("Order cancelled: code={}, from={}, amount={}", o.getOrderCode(), old, o.getTotalAmount());
        return toResponse(o);
    }

    @Override
    @Transactional
    public OrderResponse complete(UUID id, CompleteOrderRequest request) {
        log.info("Complete order id={}, note={}", id, request.note());
        requirePermission("pos:order:update");
        Order o = findAccessible(id);
        if (!PosFlow.Payment.PAID.name().equalsIgnoreCase(o.getPaymentStatus())) {
            throw new BaseException(ErrorCode.ORDER_400_UNPAID);
        }
        // Giả thiết C2: PICKUP kết ở READY, DELIVERY kết ở DELIVERING. Không ép PICKUP qua DELIVERING.
        PosFlow.Order current = PosFlow.parseOrder(o.getStatus());
        boolean pickupReady = "PICKUP".equals(o.getOrderType()) && current == PosFlow.Order.READY;
        boolean deliveryDelivering = "DELIVERY".equals(o.getOrderType()) && current == PosFlow.Order.DELIVERING;
        if (!(pickupReady || deliveryDelivering)) {
            throw new BaseException(ErrorCode.ORDER_400_INVALID_STATUS_TRANSITION);
        }
        String old = o.getStatus();
        Instant now = Instant.now();
        o.setStatus(PosFlow.Order.COMPLETED.name());
        o.setCompletedAt(now);
        orderRepository.save(o);
        writeHistory(o, old, PosFlow.Order.COMPLETED.name(), request.note());
        publishRealtimeEvent(o, OrderRealtimeEvent.TYPE_ORDER_STATUS_CHANGED,
            "Đơn hàng #" + o.getOrderCode() + " đã hoàn tất",
            "Đơn hàng đã được hoàn tất thành công.");
        log.info("Order completed: code={}, from={}", o.getOrderCode(), old);
        return toResponse(o);
    }

    @Override
    @Transactional
    public OrderResponse updatePaymentStatus(UUID id, UpdatePaymentStatusRequest request) {
        log.info("Update payment status order id={}, to={}", id, request.status());
        // TODO [VNPay Sprint]: Thay requirePermission("pos:order:update") bằng requirePaymentPermission()
        // để CUSTOMER có thể tự cập nhật sau khi VNPay callback. Xem chi tiết trong implementation_plan.md Fix 3.
        // Luật tiền 1 chiều: chỉ UNPAID -> PAID (thu tiền). PAID muốn đảo phải hủy đơn
        // để sinh refund PENDING (payment -> REFUNDED do hệ thống set), cấm un-thu tay
        // và cấm set REFUNDED tay (không có chứng từ refund đi kèm).
        requirePermission("pos:order:update");
        Order o = findAccessible(id);
        String oldPaymentStatus = o.getPaymentStatus();
        PosFlow.Payment target = PosFlow.parsePayment(request.status());
        PosFlow.Order current = PosFlow.parseOrder(o.getStatus());
        if (current == PosFlow.Order.CANCELLED || current == PosFlow.Order.REJECTED ||
            current == PosFlow.Order.COMPLETED) {
            throw new BaseException(ErrorCode.ORDER_400_INVALID_STATUS_TRANSITION,
                "Đơn ở trạng thái hiện tại không được đổi thanh toán.");
        }
        boolean alreadyPaid = PosFlow.Payment.PAID.name().equalsIgnoreCase(o.getPaymentStatus());
        if (target == PosFlow.Payment.PAID) {
            if (alreadyPaid) {
                return toResponse(o);
            }
        } else if (target == PosFlow.Payment.UNPAID) {
            if (!alreadyPaid) {
                return toResponse(o);
            }
            throw new BaseException(ErrorCode.INVALID_REQUEST,
                "Đơn đã thu tiền không được chuyển về chưa trả, hãy hủy đơn để hoàn tiền.");
        } else {
            throw new BaseException(ErrorCode.INVALID_REQUEST,
                "Hoàn tiền chỉ thực hiện qua hủy đơn, không đổi tay.");
        }
        o.setPaymentStatus(target.name());
        orderRepository.save(o);
        writeHistory(o, o.getStatus(), o.getStatus(),
            "Thu tiền -> " + target.name() + (request.note() != null ? ": " + request.note() : ""));
        publishRealtimeEvent(o, OrderRealtimeEvent.TYPE_ORDER_STATUS_CHANGED,
            "Thanh toán đơn hàng: #" + o.getOrderCode(),
            "Trạng thái thanh toán: " + target.name());
        log.info("Payment status changed: code={}, {} -> {}", o.getOrderCode(), oldPaymentStatus, target.name());
        return toResponse(o);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderHistoryResponse> history(UUID id) {
        log.info("Get history order id={}", id);
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

    // Quyết định nghiệp vụ: bỏ luồng guest, checkout bắt buộc CUSTOMER login.
    // Tham số token giữ ở DTO để tương thích FE nhưng backend ignore hoàn toàn.
    private Cart findCart(UUID customerId, UUID branchId, String token) {
        return cartRepository.findByCustomerIdAndBranchIdAndStatus(customerId, branchId, ACTIVE).orElse(null);
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

    /**
     * Điều kiện tự động CONFIRMED lúc tạo đơn: tiền mặt/COD (online phải chờ tiền thật)
     * và chi nhánh đang mở cửa. Thiếu 1 trong 2 -> ở PENDING chờ staff xử lý tay.
     */
    private boolean isAutoConfirmable(Order order) {
        boolean cashLike = "COD".equals(order.getPaymentMethod()) || "CASH".equals(order.getPaymentMethod());
        return cashLike && posBranchOpenService.isOpenNow(order.getBranchId());
    }

    private void reserveAll(Order order) {        for (OrderItem oi : itemRepository.findByOrderIdAndStatusOrderByCreatedAtAsc(order.getId(), ACTIVE)) {
            posComboService.reserveForSale(order.getBranchId(), oi.getProductId(), oi.getVariantId(),
                oi.getQuantity(), order.getId());
        }
    }

    private void releaseAll(Order order) {
        for (OrderItem oi : itemRepository.findByOrderIdAndStatusOrderByCreatedAtAsc(order.getId(), ACTIVE)) {
            posComboService.releaseForSale(order.getBranchId(), oi.getProductId(), oi.getVariantId(),
                oi.getQuantity(), order.getId());
        }
    }

    private void cancelDelivery(Order o) {
        // Lỗi ẩn orphan: hủy đơn mà delivery còn PENDING -> shipper vẫn thấy đơn.
        deliveryRepository.findByOrderId(o.getId()).ifPresent(d -> {
            if (!PosFlow.Delivery.CANCELLED.name().equals(d.getStatus())) {
                d.setStatus(PosFlow.Delivery.CANCELLED.name());
                deliveryRepository.save(d);
            }
        });
    }

    private void refundIfPaid(Order o, String reason) {
        if (!PosFlow.Payment.PAID.name().equalsIgnoreCase(o.getPaymentStatus())) {
            return;
        }
        if (refundRepository.existsByOrderIdAndStatus(o.getId(), "PENDING")) {
            return;
        }
        Refund refund = new Refund();
        refund.setOrderId(o.getId());
        refund.setRefundCode(CodeGenerator.random("RF-", refundRepository::existsByRefundCode));
        refund.setAmount(o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO);
        refund.setReason(reason != null ? reason : "Hoàn tiền đơn hủy");
        refund.setStatus("PENDING");
        refundRepository.save(refund);
        o.setPaymentStatus(PosFlow.Payment.REFUNDED.name());
        orderRepository.save(o);
    }

    private String generateOrderCode() {
        return CodeGenerator.nextDailySequence("HD-",
            prefix -> orderRepository.findTopByOrderCodeStartsWithOrderByOrderCodeDesc(prefix)
                                     .map(Order::getOrderCode),
            orderRepository::existsByOrderCode);
    }

    private OrderResponse toResponse(Order o) {
        // Bulk 1 lần: items + toppings + variant + product (ảnh), tránh N+1 theo từng dòng.
        List<OrderItem> orderItems =
            itemRepository.findByOrderIdAndStatusOrderByCreatedAtAsc(o.getId(), ACTIVE);
        Map<UUID, List<OrderItemTopping>> topsByItem = new HashMap<>();
        if (!orderItems.isEmpty()) {
            List<UUID> itemIds = orderItems.stream().map(OrderItem::getId).toList();
            for (OrderItemTopping topping :
                itemToppingRepository.findByOrderItemIdInAndStatus(itemIds, ACTIVE)) {
                topsByItem.computeIfAbsent(topping.getOrderItemId(), k -> new ArrayList<>()).add(topping);
            }
        }
        Map<UUID, ProductVariant> variantMap = new HashMap<>();
        Map<UUID, Product> productMap = new HashMap<>();
        if (!orderItems.isEmpty()) {
            List<UUID> variantIds =
                orderItems.stream().map(OrderItem::getVariantId).filter(Objects::nonNull).distinct().toList();
            if (!variantIds.isEmpty()) {
                variantRepository.findAllById(variantIds).forEach(v -> variantMap.put(v.getId(), v));
            }
            List<UUID> productIds =
                orderItems.stream().map(OrderItem::getProductId).filter(Objects::nonNull).distinct().toList();
            if (!productIds.isEmpty()) {
                productRepository.findAllById(productIds).forEach(p -> productMap.put(p.getId(), p));
            }
        }
        List<OrderItemResponse> items = orderItems.stream().map(i -> {
            List<OrderItemToppingResponse> tops =
                topsByItem.getOrDefault(i.getId(), List.of()).stream().map(
                    t -> new OrderItemToppingResponse(t.getToppingId(), t.getToppingName(), t.getQuantity(),
                                                      t.getUnitPrice(), t.getTotalPrice())).toList();
            ProductVariant variant = i.getVariantId() == null ? null : variantMap.get(i.getVariantId());
            Product product = i.getProductId() == null ? null : productMap.get(i.getProductId());
            return new OrderItemResponse(i.getId(), i.getProductCode(), i.getProductName(), i.getVariantId(),
                                         variant == null ? null : variant.getVariantCode(), i.getVariantName(),
                                         i.getQuantity(), i.getIceLevel(), i.getSugarLevel(),
                                         i.getNote(), i.getUnitPrice(), i.getTotalPrice(), i.getUnitCogsAmount(),
                                         tops, product == null ? null : product.getImageUrl());
        }).toList();
        OrderDelivery d = deliveryRepository.findByOrderId(o.getId()).orElse(null);
        DeliveryResponse dr = d == null ? null :
            new DeliveryResponse(d.getId(), d.getOrderId(), d.getShipperId(), d.getReceiverName(), d.getReceiverPhone(),
                                 d.getDeliveryAddress(), d.getDeliveryNote(), d.getDeliveryFee(), d.getStatus(),
                                 d.getAssignedAt(), d.getPickedUpAt(), d.getDeliveredAt(), d.getFailedAt(),
                                 d.getFailReason());
        String pickupSlotCode = o.getPickupTimeSlotId() == null ? null :
            pickupTimeSlotRepository.findById(o.getPickupTimeSlotId()).map(PickupTimeSlot::getSlotCode).orElse(null);
        return new OrderResponse(o.getId(), o.getOrderCode(), o.getBranchId(), o.getCustomerId(), o.getCustomerName(),
                                 o.getCustomerPhone(), o.getCustomerEmail(), o.getOrderType(), o.getStatus(),
                                 o.getPaymentMethod(), o.getPaymentStatus(), o.getSubtotalAmount(),
                                 o.getDiscountAmount(), o.getDeliveryFee(), o.getTotalAmount(), o.getTotalCogsAmount(),
                                 o.getDeliveryAddress(), o.getNote(), o.getCreatedAt(), items, dr,
                                 o.getPickupTimeSlotId(), pickupSlotCode);
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
            if (!CUSTOMER_ALLOWED_PERMISSIONS.contains(permission)) {
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
        return SecurityUtils.getCurrentPrincipalId()
            .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));
    }

    private void publishRealtimeEvent(Order o, String eventType, String title, String message) {
        if (eventPublisher == null || o == null) {
            return;
        }
        try {
            UUID shipperId = null;
            String deliveryStatus = null;
            if ("DELIVERY".equals(o.getOrderType())) {
                var dOpt = deliveryRepository.findByOrderId(o.getId());
                if (dOpt.isPresent()) {
                    shipperId = dOpt.get().getShipperId();
                    deliveryStatus = dOpt.get().getStatus();
                }
            }
            eventPublisher.publishEvent(new OrderRealtimeEvent(
                eventType,
                o.getId(),
                o.getOrderCode(),
                o.getBranchId(),
                o.getCustomerId(),
                shipperId,
                o.getStatus(),
                deliveryStatus,
                o.getPaymentStatus(),
                title,
                message,
                Instant.now()
            ));
        } catch (Exception ignored) {
        }
    }

    private UUID currentCustomerId() {
        if (!isCustomer()) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Chỉ CUSTOMER được tạo đơn từ Cart.");
        }
        return currentPrincipalId();
    }
}
