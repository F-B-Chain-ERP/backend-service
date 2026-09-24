package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.*;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.CartService;
import com.erp.backend_service.service.pos.ComboSalesService;
import com.erp.backend_service.service.pos.PosComboService;
import com.erp.core.domain.*;
import com.erp.core.dto.request.pos.AddCartItemRequest;
import com.erp.core.dto.request.pos.UpdateCartItemRequest;
import com.erp.core.dto.response.menu.ComboItemResponse;
import com.erp.core.dto.response.pos.*;
import com.erp.core.enums.PrincipalType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {
    private static final String ACTIVE = "ACTIVE";

    private final CartRepository cartRepository;
    private final CartItemRepository itemRepository;
    private final CartItemToppingRepository itemToppingRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ToppingRepository toppingRepository;
    private final ProductToppingRepository productToppingRepository;
    private final BranchToppingAvailabilityRepository branchToppingAvailabilityRepository;
    private final BranchProductAvailabilityRepository availabilityRepository;
    private final PosComboService posComboService;
    private final ComboSalesService comboSalesService;
    private final CustomerRepository customerRepository;

    public CartServiceImpl(CartRepository cartRepository, CartItemRepository itemRepository,
                           CartItemToppingRepository itemToppingRepository, ProductRepository productRepository,
                           ProductVariantRepository variantRepository, ToppingRepository toppingRepository,
                           ProductToppingRepository productToppingRepository,
                           BranchToppingAvailabilityRepository branchToppingAvailabilityRepository,
                           BranchProductAvailabilityRepository availabilityRepository,
                           PosComboService posComboService, ComboSalesService comboSalesService,
                           CustomerRepository customerRepository) {
        this.cartRepository = cartRepository;
        this.itemRepository = itemRepository;
        this.itemToppingRepository = itemToppingRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.toppingRepository = toppingRepository;
        this.productToppingRepository = productToppingRepository;
        this.branchToppingAvailabilityRepository = branchToppingAvailabilityRepository;
        this.availabilityRepository = availabilityRepository;
        this.posComboService = posComboService;
        this.comboSalesService = comboSalesService;
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(UUID branchId, String sessionToken) {
        requireCustomerPermission("pos:cart:view");
        UUID customerId = currentCustomerId();
        Cart cart = findCart(customerId, branchId, sessionToken);
        if (cart == null) {
            return new CartResponse(null, branchId, BigDecimal.ZERO, List.of());
        }
        return toResponse(cart);
    }

    @Override
    @Transactional
    public CartMutationResponse addItem(AddCartItemRequest request) {
        requireCustomerPermission("pos:cart:create");
        UUID customerId = currentCustomerId();
        // Serialize cả thao tác tạo cart đầu tiên của cùng customer. Khóa cart riêng không
        // bảo vệ được trường hợp cả hai request cùng thấy "chưa có cart".
        customerRepository.findByIdForUpdate(customerId)
            .orElseThrow(() -> new BaseException(ErrorCode.CUSTOMER_NOT_FOUND));
        Product product = productRepository.findById(request.productId())
                                           .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_PRODUCT_NOT_FOUND));
        if (!ACTIVE.equals(product.getStatus())) {
            throw new BaseException(ErrorCode.MENU_404_PRODUCT_NOT_FOUND);
        }

        BranchProductAvailability availability = availabilityRepository
            .findByBranchIdAndProductIdAndStatus(request.branchId(), product.getId(), ACTIVE)
            .orElseThrow(() -> new BaseException(ErrorCode.MENU_404_PRODUCT_NOT_FOUND));
        if (!availability.isAvailable()) {
            throw new BaseException(ErrorCode.MENU_404_PRODUCT_NOT_FOUND);
        }

        ProductVariant variant = null;
        if (request.variantId() != null) {
            variant = variantRepository.findByIdAndProductId(request.variantId(), product.getId())
                                       .orElseThrow(() -> new BaseException(ErrorCode.INVALID_REQUEST,
                                                                            "Biến thể sản phẩm không hợp lệ."));
            if (!ACTIVE.equals(variant.getStatus())) {
                throw new BaseException(ErrorCode.INVALID_REQUEST, "Biến thể sản phẩm không còn khả dụng.");
            }
        } else {
            // Giả thiết A1: SP có variant ACTIVE thì bắt buộc chọn variant.
            // Trước đây variantId=null bypass kho -> oversell ẩn.
            boolean hasActiveVariant = !variantRepository
                .findByProductIdAndStatusOrderByDisplayOrderAsc(product.getId(), ACTIVE).isEmpty();
            if (hasActiveVariant) {
                throw new BaseException(ErrorCode.INVALID_REQUEST, "Sản phẩm có size, vui lòng chọn biến thể.");
            }
        }
        Cart cart = getOrCreateCart(customerId, request.branchId(), request.sessionToken());
        String ice = normalize(request.iceLevel(), "NORMAL");
        String sugar = normalize(request.sugarLevel(), "NORMAL");
        String note = request.note();
        BigDecimal unitPrice = resolveUnitPrice(product, variant, availability);

        // So khớp theo topping/ly (không phải tổng line) để thêm 1 ly vào line 2 ly vẫn merge đúng.
        // Topping load bulk 1 lần cho cả giỏ (tránh N+1: trước đây mỗi line 1 query).
        String toppingSignature = toppingUnitSignature(request.toppings(), request.quantity());
        List<CartItem> existingItems =
            itemRepository.findByCartIdAndStatusOrderByCreatedAtAsc(cart.getId(), ACTIVE);
        Map<UUID, String> signatureByItem = toppingUnitSignatures(existingItems);
        CartItem item = existingItems.stream()
                                     .filter(i -> Objects.equals(i.getProductId(), product.getId())
                                         && Objects.equals(i.getVariantId(), request.variantId())
                                         && Objects.equals(i.getIceLevel(), ice)
                                         && Objects.equals(i.getSugarLevel(), sugar)
                                         && Objects.equals(i.getNote(), note)
                                         && Objects.equals(toppingSignature,
                                             signatureByItem.getOrDefault(i.getId(), "")))
                                     .findFirst().orElse(null);

        int newQuantity = request.quantity();
        if (item != null) {
            newQuantity += item.getQuantity();
        }
        // Validate đúng một lần với tổng quantity sau merge.
        posComboService.validateForSale(product, request.variantId(), newQuantity, request.branchId());
        if (item != null) {
            item.setQuantity(newQuantity);
            item.setUnitPrice(unitPrice);
            item.setTotalPrice(unitPrice.multiply(BigDecimal.valueOf(newQuantity)));
            itemRepository.save(item);
        } else {
            item = new CartItem();
            item.setCartId(cart.getId());
            item.setProductId(product.getId());
            item.setVariantId(request.variantId());
            item.setQuantity(request.quantity());
            item.setIceLevel(ice);
            item.setSugarLevel(sugar);
            item.setNote(note);
            item.setUnitPrice(unitPrice);
            item.setTotalPrice(unitPrice.multiply(BigDecimal.valueOf(request.quantity())));
            item.setStatus(ACTIVE);
            item = itemRepository.save(item);
        }
        saveToppings(item, request.toppings(), product.getId(), cart.getBranchId());
        recalculate(cart);
        return new CartMutationResponse(cart.getId(), item.getId(), item.getQuantity(), item.getTotalPrice(),
                                        cart.getSubtotalAmount());
    }

    @Override
    @Transactional
    public CartMutationResponse updateItem(UUID itemId, UpdateCartItemRequest request) {
        requireCustomerPermission("pos:cart:update");
        UUID customerId = currentCustomerId();
        CartItem snapshot =
            itemRepository.findById(itemId).orElseThrow(() -> new BaseException(ErrorCode.ORDER_404_CART_NOT_FOUND));
        Cart cart = cartRepository.findByIdForUpdate(snapshot.getCartId())
                                   .orElseThrow(() -> new BaseException(ErrorCode.ORDER_404_CART_NOT_FOUND));
        ensureOwner(cart, customerId);
        CartItem item = itemRepository.findByIdAndCartIdAndStatusForUpdate(itemId, cart.getId(), ACTIVE)
            .orElseThrow(() -> new BaseException(ErrorCode.ORDER_404_CART_NOT_FOUND));
        if (!ACTIVE.equals(item.getStatus())) {
            throw new BaseException(ErrorCode.ORDER_404_CART_NOT_FOUND);
        }
        if (request.quantity() != null) {
            posComboService.validateForSale(item.getProductId(), item.getVariantId(), request.quantity(),
                cart.getBranchId());
            scaleToppingsProportionally(item.getId(), item.getQuantity(), request.quantity());
            item.setQuantity(request.quantity());
        }
        if (request.iceLevel() != null) {
            item.setIceLevel(request.iceLevel());
        }
        if (request.sugarLevel() != null) {
            item.setSugarLevel(request.sugarLevel());
        }
        if (request.note() != null) {
            item.setNote(request.note());
        }
        item.setTotalPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        itemRepository.save(item);
        recalculate(cart);
        return new CartMutationResponse(cart.getId(), item.getId(), item.getQuantity(), item.getTotalPrice(),
                                        cart.getSubtotalAmount());
    }

    @Override
    @Transactional
    public CartMutationResponse deleteItem(UUID itemId) {
        requireCustomerPermission("pos:cart:delete");
        UUID customerId = currentCustomerId();
        CartItem snapshot =
            itemRepository.findById(itemId).orElseThrow(() -> new BaseException(ErrorCode.ORDER_404_CART_NOT_FOUND));
        Cart cart = cartRepository.findByIdForUpdate(snapshot.getCartId())
                                   .orElseThrow(() -> new BaseException(ErrorCode.ORDER_404_CART_NOT_FOUND));
        ensureOwner(cart, customerId);
        CartItem item = itemRepository.findByIdAndCartIdAndStatusForUpdate(itemId, cart.getId(), ACTIVE)
            .orElseThrow(() -> new BaseException(ErrorCode.ORDER_404_CART_NOT_FOUND));
        if (!ACTIVE.equals(item.getStatus())) {
            throw new BaseException(ErrorCode.ORDER_404_CART_NOT_FOUND);
        }
        itemToppingRepository.deleteByCartItemId(item.getId());
        item.setStatus("DELETED");
        itemRepository.save(item);
        recalculate(cart);
        return new CartMutationResponse(cart.getId(), itemId, null, null, cart.getSubtotalAmount());
    }

    // Quyết định nghiệp vụ: bỏ luồng guest (không có QR-bàn). Cart luôn gắn CUSTOMER login.
    // Tham số sessionToken giữ ở API/DTO để tương thích FE nhưng backend ignore hoàn toàn.
    private Cart findCart(UUID customerId, UUID branchId, String sessionToken) {
        if (customerId == null) {
            return null;
        }
        return cartRepository.findByCustomerIdAndBranchIdAndStatus(customerId, branchId, ACTIVE).orElse(null);
    }

    private Cart getOrCreateCart(UUID customerId, UUID branchId, String sessionToken) {
        Cart cart = cartRepository.findActiveForUpdate(customerId, branchId, ACTIVE).orElse(null);
        if (cart != null) {
            return cart;
        }
        Cart c = new Cart();
        c.setCustomerId(customerId);
        c.setBranchId(branchId);
        c.setStatus(ACTIVE);
        c.setSubtotalAmount(BigDecimal.ZERO);
        return cartRepository.save(c);
    }

    private void saveToppings(CartItem item, List<AddCartItemRequest.ToppingRequest> toppings,
                               UUID productId, UUID branchId) {
        // Gộp topping cũ của line + topping mới thêm (không xóa mất phần cũ khi merge).
        // quantity là TỔNG cả line; trần = maxQuantity × số ly (giả thiết A2: topping theo ly).
        Map<UUID, Integer> mergedToppings = new LinkedHashMap<>();
        for (CartItemTopping existing :
            itemToppingRepository.findByCartItemIdAndStatus(item.getId(), ACTIVE)) {
            mergedToppings.merge(existing.getToppingId(), existing.getQuantity(), Integer::sum);
        }
        if (toppings != null) {
            for (AddCartItemRequest.ToppingRequest req : toppings) {
                mergedToppings.merge(req.toppingId(), req.quantity(), Integer::sum);
            }
        }
        itemToppingRepository.deleteByCartItemId(item.getId());
        if (mergedToppings.isEmpty()) {
            return;
        }
        Set<UUID> toppingIds = mergedToppings.keySet();
        Map<UUID, Topping> toppingsById = toppingRepository.findAllById(toppingIds).stream()
            .collect(Collectors.toMap(Topping::getId, t -> t));
        Map<UUID, ProductTopping> productToppingsById = productToppingRepository
            .findByProductIdAndToppingIdInAndStatus(productId, toppingIds, ACTIVE).stream()
            .collect(Collectors.toMap(ProductTopping::getToppingId, pt -> pt, (a, b) -> a));
        Map<UUID, BranchToppingAvailability> availabilityById = branchToppingAvailabilityRepository
            .findByBranchIdAndToppingIdInAndStatus(branchId, toppingIds, ACTIVE).stream()
            .collect(Collectors.toMap(BranchToppingAvailability::getToppingId, a -> a, (a, b) -> a));
        List<CartItemTopping> rows = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : mergedToppings.entrySet()) {
            UUID toppingId = entry.getKey();
            int quantity = entry.getValue();
            Topping topping = toppingsById.get(toppingId);
            if (topping == null) {
                throw new BaseException(ErrorCode.INVALID_REQUEST, "Topping không tồn tại.");
            }
            if (!ACTIVE.equals(topping.getStatus())) {
                throw new BaseException(ErrorCode.INVALID_REQUEST, "Topping không còn khả dụng.");
            }
            ProductTopping productTopping = productToppingsById.get(toppingId);
            if (productTopping == null) {
                throw new BaseException(ErrorCode.INVALID_REQUEST, "Topping không thuộc sản phẩm.");
            }
            if (quantity > productTopping.getMaxQuantity() * item.getQuantity()) {
                throw new BaseException(ErrorCode.ORDER_400_INVALID_QUANTITY);
            }
            BranchToppingAvailability toppingAvailability = availabilityById.get(toppingId);
            if (toppingAvailability == null || !toppingAvailability.isAvailable()) {
                throw new BaseException(ErrorCode.INVALID_REQUEST, "Topping không khả dụng tại chi nhánh.");
            }
            CartItemTopping ct = new CartItemTopping();
            ct.setCartItemId(item.getId());
            ct.setToppingId(topping.getId());
            ct.setQuantity(quantity);
            ct.setUnitPrice(topping.getPrice());
            ct.setTotalPrice(topping.getPrice().multiply(BigDecimal.valueOf(quantity)));
            ct.setStatus(ACTIVE);
            rows.add(ct);
        }
        itemToppingRepository.saveAll(rows);
    }

    private BigDecimal resolveUnitPrice(Product p, ProductVariant v, BranchProductAvailability a) {
        BigDecimal base = a.getSalePrice() != null ? a.getSalePrice() : p.getBasePrice();
        return base.add(v == null || v.getPriceDelta() == null ? BigDecimal.ZERO : v.getPriceDelta());
    }

    private void recalculate(Cart cart) {
        List<CartItem> items = itemRepository.findByCartIdAndStatusOrderByCreatedAtAsc(cart.getId(), ACTIVE);
        BigDecimal itemTotal = items.stream().map(CartItem::getTotalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal toppingTotal = items.isEmpty() ? BigDecimal.ZERO
            : itemToppingRepository.findByCartItemIdInAndStatus(
                    items.stream().map(CartItem::getId).toList(), ACTIVE).stream()
                .map(CartItemTopping::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal subtotal = itemTotal.add(toppingTotal);
        cart.setSubtotalAmount(subtotal);
        cartRepository.save(cart);
    }

    /**
     * Chữ ký topping TÍNH THEO LY (id:sluợng-mỗi-ly) để merge đúng:
     * thêm 1 ly vào line 2 ly cùng công thức vẫn gộp 1 line.
     * Không chia hết -> null (coi như công thức khác, tách line riêng).
     */
    private String toppingUnitSignature(List<AddCartItemRequest.ToppingRequest> toppings, int itemQty) {
        if (toppings == null || toppings.isEmpty()) {
            return "";
        }
        if (itemQty <= 0) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (AddCartItemRequest.ToppingRequest t : toppings) {
            if (t.quantity() % itemQty != 0) {
                return null;
            }
            parts.add(t.toppingId() + ":" + (t.quantity() / itemQty));
        }
        Collections.sort(parts);
        return String.join(",", parts);
    }

    private String toppingUnitSignature(UUID cartItemId, int itemQty) {
        List<CartItemTopping> list = itemToppingRepository.findByCartItemIdAndStatus(cartItemId, ACTIVE);
        if (list.isEmpty()) {
            return "";
        }
        if (itemQty <= 0) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (CartItemTopping t : list) {
            if (t.getQuantity() % itemQty != 0) {
                return null;
            }
            parts.add(t.getToppingId() + ":" + (t.getQuantity() / itemQty));
        }
        Collections.sort(parts);
        return String.join(",", parts);
    }

    /** Bulk chữ ký topping/ly cho cả giỏ trong 1 query (tránh N+1 ở so khớp merge). */
    private Map<UUID, String> toppingUnitSignatures(List<CartItem> items) {
        Map<UUID, List<CartItemTopping>> byItem = new HashMap<>();
        if (!items.isEmpty()) {
            List<UUID> ids = items.stream().map(CartItem::getId).toList();
            for (CartItemTopping t : itemToppingRepository.findByCartItemIdInAndStatus(ids, ACTIVE)) {
                byItem.computeIfAbsent(t.getCartItemId(), k -> new ArrayList<>()).add(t);
            }
        }
        Map<UUID, String> result = new HashMap<>();
        for (CartItem item : items) {
            List<CartItemTopping> list = byItem.getOrDefault(item.getId(), List.of());
            if (list.isEmpty()) {
                result.put(item.getId(), "");
                continue;
            }
            int itemQty = item.getQuantity();
            if (itemQty <= 0) {
                result.put(item.getId(), null);
                continue;
            }
            List<String> parts = new ArrayList<>();
            boolean divisible = true;
            for (CartItemTopping t : list) {
                if (t.getQuantity() % itemQty != 0) {
                    divisible = false;
                    break;
                }
                parts.add(t.getToppingId() + ":" + (t.getQuantity() / itemQty));
            }
            if (!divisible) {
                result.put(item.getId(), null);
                continue;
            }
            Collections.sort(parts);
            result.put(item.getId(), String.join(",", parts));
        }
        return result;
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItem> cartItems = itemRepository.findByCartIdAndStatusOrderByCreatedAtAsc(cart.getId(), ACTIVE);

        Set<UUID> productIds = cartItems.stream().map(CartItem::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
                                                          .collect(Collectors.toMap(Product::getId, p -> p));

        // Toàn bộ combo trong giỏ, batch một lần (combo_item -> variant -> product), không N+1 theo line.
        List<UUID> comboProductIds = productMap.values().stream()
                .filter(Product::isCombo)
                .map(Product::getId)
                .toList();
        Map<UUID, List<ComboItemResponse>> comboMap = comboProductIds.isEmpty()
                ? Map.of()
                : comboSalesService.comboItemsByProduct(comboProductIds);

        Set<UUID> variantIds = cartItems.stream().map(CartItem::getVariantId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, ProductVariant> variantMap = variantIds.isEmpty() ? Map.of() :
            variantRepository.findAllById(variantIds).stream()
                              .collect(Collectors.toMap(ProductVariant::getId, v -> v));

        Map<UUID, List<CartItemTopping>> toppingsByItem = new HashMap<>();
        if (!cartItems.isEmpty()) {
            itemToppingRepository.findByCartItemIdInAndStatus(
                    cartItems.stream().map(CartItem::getId).toList(), ACTIVE)
                .forEach(t -> toppingsByItem.computeIfAbsent(t.getCartItemId(), ignored -> new ArrayList<>()).add(t));
        }

        Set<UUID> toppingIds = toppingsByItem.values().stream()
            .flatMap(Collection::stream)
            .map(CartItemTopping::getToppingId)
            .collect(Collectors.toSet());
        Map<UUID, Topping> toppingMap = toppingIds.isEmpty() ? Map.of() :
            toppingRepository.findAllById(toppingIds).stream()
                              .collect(Collectors.toMap(Topping::getId, t -> t));

        List<CartItemResponse> items = cartItems.stream().map(i -> {
            Product p = productMap.get(i.getProductId());
            ProductVariant v = i.getVariantId() == null ? null : variantMap.get(i.getVariantId());
            List<CartItemToppingResponse> tops = toppingsByItem.getOrDefault(i.getId(), List.of()).stream().map(t -> {
                Topping tp = toppingMap.get(t.getToppingId());
                return new CartItemToppingResponse(t.getToppingId(), tp == null ? null : tp.getName(),
                                                   t.getQuantity(), t.getUnitPrice(), t.getTotalPrice());
            }).toList();
            List<ComboItemResponse> comboItems = (p != null && p.isCombo())
                    ? comboMap.getOrDefault(p.getId(), List.of()).stream()
                            .map(c -> scaleQuantity(c, i.getQuantity()))
                            .toList()
                    : List.of();
            return new CartItemResponse(i.getId(), i.getProductId(), p == null ? null : p.getCode(),
                                        p == null ? null : p.getName(),
                                        i.getVariantId(), v == null ? null : v.getVariantName(), i.getQuantity(),
                                        i.getIceLevel(), i.getSugarLevel(), i.getNote(), i.getUnitPrice(),
                                        i.getTotalPrice(), tops, comboItems);
        }).toList();
        return new CartResponse(cart.getId(), cart.getBranchId(), cart.getSubtotalAmount(), items);
    }

    /** Nhân quantity thành phần combo theo số lượng combo đang ở trong giỏ. */
    private ComboItemResponse scaleQuantity(ComboItemResponse item, int factor) {
        if (factor <= 1) {
            return item;
        }
        int newQty = item.quantity() * factor;
        BigDecimal lineTotal = item.variantPrice() != null
                ? item.variantPrice().multiply(BigDecimal.valueOf(newQty))
                : null;
        return new ComboItemResponse(item.comboItemId(), item.variantId(), item.variantCode(), item.variantName(),
                item.sizeLabel(), item.productId(), item.productCode(), item.productName(), item.variantPrice(),
                newQty, item.isSubstitutable(), item.status(), lineTotal);
    }

    /**
     * Giả thiết A2: topping tính theo ly. Đổi số ly thì scale topping theo tỉ lệ,
     * topping cũ phải chia hết cho số ly cũ, nếu không bắt FE gửi lại topping.
     * Lỗi ẩn trước đây: tăng ly nhưng giữ topping -> thiếu tiền + thiếu NVL.
     */
    private void scaleToppingsProportionally(UUID cartItemId, int oldQty, int newQty) {
        if (oldQty == newQty) {
            return;
        }
        var toppings = itemToppingRepository.findByCartItemIdAndStatus(cartItemId, ACTIVE);
        for (var topping : toppings) {
            int totalQty = topping.getQuantity();
            if (totalQty % oldQty != 0) {
                throw new BaseException(ErrorCode.INVALID_REQUEST,
                    "Đổi số lượng thì topping phải chia đều theo ly, vui lòng chọn lại topping.");
            }
            int perUnit = totalQty / oldQty;
            int scaled = perUnit * newQty;
            topping.setQuantity(scaled);
            topping.setTotalPrice(topping.getUnitPrice().multiply(BigDecimal.valueOf(scaled)));
            // flush một batch ở cuối thay vì một save/row.
        }
        itemToppingRepository.saveAll(toppings);
    }

    private void requireCustomerPermission(String permission) {
        if (SecurityUtils.getCurrentPrincipalType().orElse(null) == PrincipalType.CUSTOMER) {
            return;
        }
        if (!SecurityUtils.hasPermission(permission) && !SecurityUtils.hasPermission(permission.replace("pos:", ""))) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
    }

    private UUID currentCustomerId() {
        if (SecurityUtils.getCurrentPrincipalType().orElse(null) != PrincipalType.CUSTOMER) {
            throw new BaseException(ErrorCode.UNAUTHORIZED, "Cart chỉ dành cho CUSTOMER.");
        }
        return SecurityUtils.getCurrentPrincipalId().orElseThrow(() -> new BaseException(ErrorCode.UNAUTHENTICATED));
    }

    private void ensureOwner(Cart cart, UUID customerId) {
        if (!Objects.equals(cart.getCustomerId(), customerId)) {
            throw new BaseException(ErrorCode.CROSS_SCOPE_DENIED);
        }
    }

    private String normalize(String s, String d) {
        return s == null || s.isBlank() ? d : s.trim();
    }
}
