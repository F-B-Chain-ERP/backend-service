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
import com.erp.backend_service.service.pos.PosMaterialConsumptionService;
import com.erp.backend_service.service.pos.PosShipperAssignService;
import com.erp.backend_service.util.CodeGenerator;
import com.erp.core.domain.*;
import com.erp.core.dto.request.pos.*;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.pos.*;
import com.erp.core.enums.PrincipalType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.erp.backend_service.event.OrderRealtimeEvent;
import com.erp.backend_service.event.KdsOrderEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.time.format.DateTimeFormatter;

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
    private final PosMaterialConsumptionService posMaterialConsumptionService;

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
                            PickupTimeSlotRepository pickupTimeSlotRepository,
                            ApplicationEventPublisher eventPublisher,
                            PosMaterialConsumptionService posMaterialConsumptionService) {
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
        this.posMaterialConsumptionService = posMaterialConsumptionService;
    }

    @Override
    @Transactional
    public OrderResponse create(String idempotencyKey, CreateOrderRequest request) {
        requireCustomerPermission("pos:order:create");
        UUID customerId = currentCustomerId();
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
            // claim nằm cùng transaction; rollback sẽ tự xóa PROCESSING vừa tạo.
            // Không ghi FAILED trong catch vì transaction có thể đã rollback-only.
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

        // Checkout độc quyền cart: chặn double-click/hai request cùng copy và consume một giỏ.
        Cart cart = cartRepository.findActiveForUpdate(customerId, request.branchId(), ACTIVE).orElse(null);
        if (cart == null) {
            throw new BaseException(ErrorCode.ORDER_404_CART_NOT_FOUND);
        }
        List<CartItem> cartItems = cartItemRepository.findByCartIdAndStatusOrderByCreatedAtAsc(cart.getId(), ACTIVE);
        if (cartItems.isEmpty()) {
            throw new BaseException(ErrorCode.ORDER_400_CART_EMPTY);
        }
        Set<UUID> productIds = cartItems.stream().map(CartItem::getProductId)
            .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Map<UUID, Product> productsById = productRepository.findAllById(productIds).stream()
            .collect(java.util.stream.Collectors.toMap(Product::getId, p -> p));
        Map<UUID, BranchProductAvailability> availabilityByProduct = availabilityRepository
            .findByBranchIdAndProductIdInAndStatus(request.branchId(), productIds, ACTIVE).stream()
            .collect(java.util.stream.Collectors.toMap(BranchProductAvailability::getProductId,
                a -> a, (a, b) -> a));
        Set<UUID> variantIds = cartItems.stream().map(CartItem::getVariantId)
            .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Map<UUID, ProductVariant> variantsById = variantRepository.findAllById(variantIds).stream()
            .collect(java.util.stream.Collectors.toMap(ProductVariant::getId, v -> v));
        Set<UUID> productsWithActiveVariants = variantRepository
            .findByProductIdInAndStatus(productIds, ACTIVE).stream()
            .map(ProductVariant::getProductId).collect(java.util.stream.Collectors.toSet());
        List<CartItemTopping> allCartToppings = cartItems.isEmpty() ? List.of()
            : cartItemToppingRepository.findByCartItemIdInAndStatus(
                cartItems.stream().map(CartItem::getId).toList(), ACTIVE);
        Map<UUID, List<CartItemTopping>> cartToppingsByItem = allCartToppings.stream()
            .collect(java.util.stream.Collectors.groupingBy(CartItemTopping::getCartItemId));
        Set<UUID> toppingIds = allCartToppings.stream().map(CartItemTopping::getToppingId)
            .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Map<UUID, Topping> toppingsById = toppingRepository.findAllById(toppingIds).stream()
            .collect(java.util.stream.Collectors.toMap(Topping::getId, t -> t));
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
            Product p = productsById.get(ci.getProductId());
            if (p == null) {
                throw new BaseException(ErrorCode.ORDER_404_PRODUCT_NOT_FOUND);
            }
            if (!"ACTIVE".equals(p.getStatus())) {
                throw new BaseException(ErrorCode.ORDER_404_PRODUCT_NOT_FOUND);
            }
            BranchProductAvailability productAvailability = availabilityByProduct.get(p.getId());
            if (productAvailability == null || !productAvailability.isAvailable()) {
                throw new BaseException(ErrorCode.ORDER_404_PRODUCT_NOT_FOUND);
            }
            if (ci.getVariantId() != null) {
                ProductVariant variant = variantsById.get(ci.getVariantId());
                if (variant == null || !p.getId().equals(variant.getProductId())) {
                    throw new BaseException(ErrorCode.INVALID_REQUEST, "Biến thể sản phẩm không hợp lệ.");
                }
                if (!ACTIVE.equals(variant.getStatus())) {
                    throw new BaseException(ErrorCode.INVALID_REQUEST, "Biến thể sản phẩm không còn khả dụng.");
                }
            } else {
                // Chốt trừ kho: SP có variant ACTIVE thì bắt buộc chọn variant (giả thiết A1,
                // giống CartServiceImpl). Chặn đơn lọt với variantId=null bypass kho dù giỏ đã check.
                boolean hasActiveVariant = productsWithActiveVariants.contains(p.getId());
                if (hasActiveVariant) {
                    throw new BaseException(ErrorCode.INVALID_REQUEST, "Sản phẩm có size, vui lòng chọn biến thể.");
                }
            }
            // Giả thiết B1: subtotal phải khớp Cart (item + topping). Giả thiết combo A4 tái validate ở chốt đơn.
            posComboService.validateForSale(p, ci.getVariantId(), ci.getQuantity(),
                request.branchId());
            subtotal = subtotal.add(ci.getTotalPrice());
            BigDecimal toppingsTotal = cartToppingsByItem.getOrDefault(ci.getId(), List.of())
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
            // Chỉ preview ở đây. Lock hạn mức được dời sát lúc ghi usage để không khóa
            // voucher phổ biến trong toàn bộ quá trình copy item/trừ tồn/tạo delivery.
            voucher = preview;
            // Giả thiết B2: PERCENT làm tròn HALF_UP 2 decimals để không crash số lẻ.
            discount = calculateDiscount(voucher, subtotal);
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

        Map<UUID, BigDecimal> cogsByVariant = posCogsService.unitCogsByVariantIds(variantIds);
        List<OrderItemTopping> orderToppings = new ArrayList<>();
        for (CartItem ci : checked) {
            Product p = productsById.get(ci.getProductId());
            ProductVariant v = ci.getVariantId() == null ? null : variantsById.get(ci.getVariantId());
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
            BigDecimal unitCogs = ci.getVariantId() == null ? BigDecimal.ZERO
                : cogsByVariant.getOrDefault(ci.getVariantId(), BigDecimal.ZERO);
            oi.setUnitCogsAmount(unitCogs);
            oi.setStatus(ACTIVE);
            oi = itemRepository.save(oi);
            o.setTotalCogsAmount(o.getTotalCogsAmount().add(unitCogs.multiply(BigDecimal.valueOf(ci.getQuantity()))));
            for (CartItemTopping ct : cartToppingsByItem.getOrDefault(ci.getId(), List.of())) {
                Topping t = toppingsById.get(ct.getToppingId());
                if (t == null) {
                    throw new BaseException(ErrorCode.INVALID_REQUEST, "Topping không tồn tại.");
                }
                OrderItemTopping ot = new OrderItemTopping();
                ot.setOrderItemId(oi.getId());
                ot.setToppingId(t.getId());
                ot.setToppingCode(t.getCode());
                ot.setToppingName(t.getName());
                ot.setQuantity(ct.getQuantity());
                ot.setUnitPrice(ct.getUnitPrice());
                ot.setTotalPrice(ct.getTotalPrice());
                ot.setStatus(ACTIVE);
                orderToppings.add(ot);
            }
        }
        itemToppingRepository.saveAll(orderToppings);
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
            // Trừ NVL realtime cùng lúc reserve tồn-ly (1 lần duy nhất tại CONFIRMED).
            posMaterialConsumptionService.deductForOrder(o);
            posShipperAssignService.autoAssign(o);
            writeHistory(o, confirmedOld, PosFlow.Order.CONFIRMED.name(),
                "Tự động xác nhận: chi nhánh mở cửa và thanh toán tiền mặt/COD");
            // KDS: 1 đơn CONFIRMED = 1 ticket BAR (station cố định, queue_no theo ngày).
            publishKdsEvent(KdsOrderEvent.Action.CREATE, o.getId(), o.getStatus(), null);
        }
        if (voucher != null) {
            voucher = voucherRepository.findByIdForUpdate(voucher.getId()).orElseThrow(
                () -> new BaseException(ErrorCode.INVALID_REQUEST, "Voucher không hợp lệ."));
            Instant lockedNow = Instant.now();
            if (!ACTIVE.equals(voucher.getStatus()) || lockedNow.isBefore(voucher.getStartAt())
                || lockedNow.isAfter(voucher.getEndAt()) || subtotal.compareTo(voucher.getMinOrderAmount()) < 0) {
                throw new BaseException(ErrorCode.INVALID_REQUEST, VOUCHER_INVALID_MSG);
            }
            if (voucher.getUsageLimit() != null && voucher.getUsedCount() >= voucher.getUsageLimit()) {
                throw new BaseException(ErrorCode.INVALID_REQUEST, "Voucher đã hết lượt sử dụng.");
            }
            if (voucher.getUsageLimitPerCustomer() != null &&
                voucherUsageRepository.countByVoucherIdAndCustomerIdAndStatus(voucher.getId(), customerId, ACTIVE) >=
                    voucher.getUsageLimitPerCustomer()) {
                throw new BaseException(ErrorCode.INVALID_REQUEST, "Bạn đã sử dụng voucher quá số lần cho phép.");
            }
            discount = calculateDiscount(voucher, subtotal);
            o.setDiscountAmount(discount);
            o.setTotalAmount(subtotal.subtract(discount).add(fee).max(BigDecimal.ZERO));
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
        cartItemToppingRepository.deleteAll(allCartToppings);
        cartItemRepository.deleteAll(cartItems);
        cart.setStatus("CONVERTED");
        cart.setSubtotalAmount(BigDecimal.ZERO);
        cartRepository.save(cart);
        publishRealtimeEvent(o, OrderRealtimeEvent.TYPE_ORDER_CREATED,
            "Đơn hàng mới: #" + o.getOrderCode(),
            "Đơn hàng mới từ " + (o.getCustomerName() != null ? o.getCustomerName() : "Khách hàng") + " (" + o.getTotalAmount() + "đ)");
        return toResponse(o);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> list(UUID branchId, String orderType, String status, LocalDate fromDate,
                                                   LocalDate toDate, String search, int page, int size) {
        return list(branchId, orderType, status, null, null, fromDate, toDate, search, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> list(UUID branchId, String orderType, String status,
                                                   String paymentStatus, String paymentMethod,
                                                   LocalDate fromDate, LocalDate toDate,
                                                   String search, int page, int size) {
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
        String safePaymentStatus = normalizePaymentStatus(paymentStatus);
        String safePaymentMethod = normalizePaymentMethod(paymentMethod);
        Page<Order> p = orderRepository.findAll(
            OrderSpecifications.filter(effectiveBranch, customerId, orderType, status,
                safePaymentStatus, safePaymentMethod, from, to, search),
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResponse<>(p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages(),
                                  p.getContent().stream().map(
                                      o -> new OrderSummaryResponse(o.getId(), o.getOrderCode(), o.getBranchId(),
                                                                    o.getOrderType(), o.getCustomerName(),
                                                                    o.getTotalAmount(), o.getStatus(),
                                                                    o.getCreatedAt(),
                                                                    o.getPaymentMethod(), o.getPaymentStatus())).toList());
    }

    private String normalizePaymentStatus(String v) {
        if (v == null || v.isBlank()) return null;
        String s = v.trim().toUpperCase();
        if (s.equals("UNPAID") || s.equals("PAID") || s.equals("REFUNDED")) return s;
        return null;
    }

    private String normalizePaymentMethod(String v) {
        if (v == null || v.isBlank()) return null;
        String s = v.trim().toUpperCase();
        if (s.equals("CASH") || s.equals("COD") || s.equals("VNPAY") || s.equals("MOMO")
                || s.equals("BANK_TRANSFER")) return s;
        return null;
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
        Order o = findAccessibleForUpdate(id);
        PosFlow.Order target = PosFlow.parseOrder(request.status());
        PosFlow.Order current = PosFlow.parseOrder(o.getStatus());
        // Chốt hủy: đơn DELIVERY đang READY sau giao thất bại (delivery FAILED) được hủy
        // để thoát kẹt (khách từ chối hàng). Các ca khác giữ machine PosFlow strict.
        boolean failedReturnCancel = target == PosFlow.Order.CANCELLED
            && isFailedDeliveryReturn(o.getOrderType(), current, o.getId());
        if (!failedReturnCancel) {
            PosFlow.requireOrderTransition(current, target);
        }
        // Chốt luồng thực tế: đơn DELIVERY đi giao từ màn Giao hàng
        // (assign -> picked_up -> delivering), cấm bấm READY -> DELIVERING tay ở màn Đơn
        // để không vòng qua shipper. DeliveryService tự kéo Order khi shipper đi giao.
        if (target == PosFlow.Order.DELIVERING) {
            throw new BaseException(ErrorCode.ORDER_400_INVALID_STATUS_TRANSITION,
                "Đơn giao hàng đi giao từ màn Giao hàng (shipper lấy hàng -> đi giao).");
        }
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
            // Chốt giao hàng: đơn DELIVERY chỉ COMPLETED sau khi shipper DELIVERED.
            requireDeliveredForComplete(o);
        }
        String old = o.getStatus();
        o.setStatus(target.name());
        Instant now = Instant.now();
        switch (target) {
            case CONFIRMED -> {
                o.setConfirmedAt(now);
                // Giả thiết C1: reserve tồn ngay khi quán nhận đơn (lock + log), khỏi oversell.
                reserveAll(o);
                // Trừ NVL realtime cùng lúc (thiếu là chặn xác nhận ngay tại đây).
                posMaterialConsumptionService.deductForOrder(o);
                // Đơn giao: thử gán shipper rảnh nhất luôn, không có xe thì chờ gán tay.
                posShipperAssignService.autoAssign(o);
            }
            case PREPARING -> o.setPreparedAt(now);
            case READY -> o.setReadyAt(now);
            case DELIVERING -> o.setDeliveringAt(now);
            case COMPLETED -> {
                // Đã reserve ở CONFIRMED nên không trừ lần 2 (lỗi ẩn double-deduct cũ).
                // NVL cũng đã trừ realtime lúc CONFIRMED, hoàn tất không động tồn nữa.
                o.setCompletedAt(now);
            }
            case CANCELLED -> {
                o.setCancelledAt(now);
                // PENDING chưa reserve nên không hoàn (hoàn thừa sẽ phình tồn).
                // READY hậu giao thất bại cũng không hoàn: hàng đã làm xong, tính hao hụt.
                if (current != PosFlow.Order.PENDING && !failedReturnCancel) {
                    releaseAll(o);
                    posMaterialConsumptionService.releaseForOrder(o);
                }
                // Giữ thứ tự lock giống lúc tạo đơn: tồn/NVL trước, voucher sau.
                restoreVoucher(o);
                cancelDelivery(o);
                // Hủy đơn đã PAID phải sinh refund cho kế toán (kể cả hủy tay qua updateStatus).
                refundIfPaid(o, request.note());
            }
            case REJECTED -> {
                o.setRejectedAt(now);
                if (current != PosFlow.Order.PENDING) {
                    releaseAll(o);
                    posMaterialConsumptionService.releaseForOrder(o);
                }
                restoreVoucher(o);
                cancelDelivery(o);
            }
            default -> {
            }
        }
        orderRepository.save(o);
        // KDS theo Order (cùng transaction, không sửa DB):
        // CONFIRMED -> tạo ticket BAR; PREPARING/READY -> kéo ticket theo;
        // DELIVERING/COMPLETED -> dọn board (SERVED); CANCELLED/REJECTED -> hủy ticket.
        // KDS chỉ làm tới READY, Order bấm nốt phần còn lại.
        if (target == PosFlow.Order.CONFIRMED) {
            publishKdsEvent(KdsOrderEvent.Action.CREATE, o.getId(), target.name(), null);
        } else if (target == PosFlow.Order.PREPARING || target == PosFlow.Order.READY) {
            publishKdsEvent(KdsOrderEvent.Action.SYNC, o.getId(), target.name(), null);
        } else if (target == PosFlow.Order.DELIVERING || target == PosFlow.Order.COMPLETED) {
            publishKdsEvent(KdsOrderEvent.Action.SERVE, o.getId(), target.name(), null);
        } else if (target == PosFlow.Order.CANCELLED || target == PosFlow.Order.REJECTED) {
            publishKdsEvent(KdsOrderEvent.Action.CANCEL, o.getId(), target.name(), request.note());
        }
        UUID by = currentPrincipalId();
        writeHistory(o, old, target.name(), request.note());
        publishRealtimeEvent(o, OrderRealtimeEvent.TYPE_ORDER_STATUS_CHANGED,
            "Cập nhật đơn hàng: #" + o.getOrderCode(),
            "Đơn hàng #" + o.getOrderCode() + " đã chuyển sang trạng thái " + target.name());
        return new OrderStatusResponse(o.getId(), o.getOrderCode(), old, target.name(), by, now);
    }

    @Override
    @Transactional
    public OrderResponse cancel(UUID id, CancelOrderRequest request) {
        requirePermission("pos:order:cancel");
        Order o = findAccessibleForUpdate(id);
        PosFlow.Order current = PosFlow.parseOrder(o.getStatus());
        // Chốt hủy: thêm READY hậu giao thất bại (đơn DELIVERY + delivery FAILED, khách từ
        // chối hàng) để thoát kẹt. Các ca READY khác vẫn cấm hủy (hàng đã làm xong).
        boolean failedReturn = isFailedDeliveryReturn(o.getOrderType(), current, o.getId());
        if (!(current == PosFlow.Order.PENDING || current == PosFlow.Order.CONFIRMED ||
            current == PosFlow.Order.PREPARING || failedReturn)) {
            throw new BaseException(ErrorCode.ORDER_400_ORDER_NOT_CANCELLABLE);
        }
        String old = o.getStatus();
        o.setStatus(PosFlow.Order.CANCELLED.name());
        o.setCancelReason(request.reason());
        o.setCancelledAt(Instant.now());
        orderRepository.save(o);
        // Chỉ hoàn tồn nếu đơn đã từng reserve (CONFIRMED trở đi); PENDING thì chưa trừ.
        // READY hậu giao thất bại không hoàn: hàng đã làm xong, tính hao hụt.
        if (current != PosFlow.Order.PENDING && !failedReturn) {
            releaseAll(o);
            posMaterialConsumptionService.releaseForOrder(o);
        }
        restoreVoucher(o);
        cancelDelivery(o);
        // Giả thiết D1: hủy đơn đã PAID phải sinh refund PENDING cho kế toán, tránh mất tiền khách.
        refundIfPaid(o, request.reason());
        publishKdsEvent(KdsOrderEvent.Action.CANCEL, o.getId(), o.getStatus(), request.reason());
        writeHistory(o, old, PosFlow.Order.CANCELLED.name(), request.note() != null ? request.note() : request.reason());
        publishRealtimeEvent(o, OrderRealtimeEvent.TYPE_ORDER_STATUS_CHANGED,
            "Đơn hàng #" + o.getOrderCode() + " đã bị hủy",
            "Lý do: " + (request.reason() != null ? request.reason() : "Khách hủy"));
        return toResponse(o);
    }

    @Override
    @Transactional
    public OrderResponse complete(UUID id, CompleteOrderRequest request) {
        requirePermission("pos:order:update");
        Order o = findAccessibleForUpdate(id);
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
        // Chốt giao hàng: đơn DELIVERY chỉ COMPLETED sau khi shipper DELIVERED.
        requireDeliveredForComplete(o);
        String old = o.getStatus();
        Instant now = Instant.now();
        o.setStatus(PosFlow.Order.COMPLETED.name());
        o.setCompletedAt(now);
        orderRepository.save(o);
        // Hoàn tất đơn -> dọn board bếp (ticket -> SERVED).
        publishKdsEvent(KdsOrderEvent.Action.SERVE, o.getId(), o.getStatus(), null);
        writeHistory(o, old, PosFlow.Order.COMPLETED.name(), request.note());
        publishRealtimeEvent(o, OrderRealtimeEvent.TYPE_ORDER_STATUS_CHANGED,
            "Đơn hàng #" + o.getOrderCode() + " đã hoàn tất",
            "Đơn hàng đã được hoàn tất thành công.");
        return toResponse(o);
    }

    @Override
    @Transactional
    public OrderResponse updatePaymentStatus(UUID id, UpdatePaymentStatusRequest request) {
        // TODO [VNPay Sprint]: Thay requirePermission("pos:order:update") bằng requirePaymentPermission()
        // để CUSTOMER có thể tự cập nhật sau khi VNPay callback. Xem chi tiết trong implementation_plan.md Fix 3.
        // Luật tiền 1 chiều: chỉ UNPAID -> PAID (thu tiền). PAID muốn đảo phải hủy đơn
        // để sinh refund PENDING (payment -> REFUNDED do hệ thống set), cấm un-thu tay
        // và cấm set REFUNDED tay (không có chứng từ refund đi kèm).
        requirePermission("pos:order:update");
        Order o = findAccessible(id);
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

    private Order findAccessibleForUpdate(UUID id) {
        Order o = orderRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new BaseException(ErrorCode.ORDER_404_ORDER_NOT_FOUND));
        if (isCustomer()) {
            if (!Objects.equals(o.getCustomerId(), currentPrincipalId())) {
                throw new BaseException(ErrorCode.CROSS_SCOPE_DENIED);
            }
        } else {
            dataScopeHelper.enforceBranchAccess(o.getBranchId());
        }
        return o;
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
            voucherRepository.findByIdForUpdate(vu.getVoucherId()).ifPresent(v -> {
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

    /**
     * Chốt luồng giao: đơn DELIVERY chỉ COMPLETED sau khi shipper DELIVERED.
     * Shipper bấm giao xong ở màn Giao hàng (tự hoàn tất nếu đủ PAID); màn Đơn chỉ thu
     * nốt tiền + hoàn tất ca DELIVERED + UNPAID ngoài COD. Bỏ qua khi thiếu delivery
     * record (dữ liệu cũ) để không kẹt đơn.
     */
    private void requireDeliveredForComplete(Order o) {
        if (!"DELIVERY".equals(o.getOrderType())) {
            return;
        }
        boolean delivered = deliveryRepository.findByOrderId(o.getId())
            .map(d -> PosFlow.Delivery.DELIVERED.name().equals(d.getStatus()))
            .orElse(true);
        if (!delivered) {
            throw new BaseException(ErrorCode.ORDER_400_INVALID_STATUS_TRANSITION,
                "Shipper chưa bấm giao xong. Đơn giao hoàn tất sau khi giao xong ở màn Giao hàng.");
        }
    }

    /**
     * Chốt luồng hủy: đơn DELIVERY đang READY mà delivery FAILED (giao thất bại, khách
     * từ chối) được hủy để thoát kẹt. Gán lại shipper (ASSIGNED/...) thì khóa lại cho
     * tới lần FAILED tiếp theo.
     */
    private boolean isFailedDeliveryReturn(String orderType, PosFlow.Order currentStatus, UUID orderId) {
        if (!"DELIVERY".equals(orderType) || currentStatus != PosFlow.Order.READY) {
            return false;
        }
        return deliveryRepository.findByOrderId(orderId)
            .map(d -> PosFlow.Delivery.FAILED.name().equals(d.getStatus()))
            .orElse(false);
    }

    private void reserveAll(Order order) {
        List<OrderItem> items = new ArrayList<>(
            itemRepository.findByOrderIdAndStatusOrderByCreatedAtAsc(order.getId(), ACTIVE));
        Map<UUID, Product> products = productRepository.findAllById(
                items.stream().map(OrderItem::getProductId).filter(Objects::nonNull).distinct().toList()).stream()
            .collect(java.util.stream.Collectors.toMap(Product::getId, p -> p));
        List<PosComboService.SaleLine> lines = new ArrayList<>();
        for (OrderItem oi : items) {
            Product product = products.get(oi.getProductId());
            if (product == null) {
                throw new BaseException(ErrorCode.ORDER_404_PRODUCT_NOT_FOUND);
            }
            lines.add(new PosComboService.SaleLine(oi.getProductId(), oi.getVariantId(), oi.getQuantity(),
                product.isCombo()));
        }
        posComboService.reserveAllForSale(order.getBranchId(), lines, order.getId());
    }

    private void releaseAll(Order order) {
        List<OrderItem> items = new ArrayList<>(
            itemRepository.findByOrderIdAndStatusOrderByCreatedAtAsc(order.getId(), ACTIVE));
        Map<UUID, Product> products = productRepository.findAllById(
                items.stream().map(OrderItem::getProductId).filter(Objects::nonNull).distinct().toList()).stream()
            .collect(java.util.stream.Collectors.toMap(Product::getId, p -> p));
        List<PosComboService.SaleLine> lines = new ArrayList<>();
        for (OrderItem oi : items) {
            Product product = products.get(oi.getProductId());
            if (product != null) {
                lines.add(new PosComboService.SaleLine(oi.getProductId(), oi.getVariantId(), oi.getQuantity(),
                    product.isCombo()));
            }
        }
        posComboService.releaseAllForSale(order.getBranchId(), lines, order.getId());
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
        // Không dùng max+1: DB server không có unique order_code, hai instance có thể đọc
        // cùng max rồi sinh trùng. Hậu tố ngẫu nhiên giữ prefix ngày và không cần khóa dài.
        String prefix = "HD-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        return CodeGenerator.random(prefix, 12, orderRepository::existsByOrderCode);
    }

    private BigDecimal calculateDiscount(Voucher voucher, BigDecimal subtotal) {
        BigDecimal value = "PERCENT".equalsIgnoreCase(voucher.getDiscountType())
            ? subtotal.multiply(voucher.getDiscountValue())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
            : voucher.getDiscountValue();
        if (voucher.getMaxDiscountAmount() != null) {
            value = value.min(voucher.getMaxDiscountAmount());
        }
        return value.min(subtotal).max(BigDecimal.ZERO);
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

    private void publishKdsEvent(KdsOrderEvent.Action action, UUID orderId, String status, String reason) {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new KdsOrderEvent(action, orderId, status, reason));
        }
    }

    private UUID currentCustomerId() {
        if (!isCustomer()) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Chỉ CUSTOMER được tạo đơn từ Cart.");
        }
        return currentPrincipalId();
    }
}
