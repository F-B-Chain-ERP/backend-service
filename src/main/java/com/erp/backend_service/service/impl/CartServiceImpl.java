package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.*;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.CartService;
import com.erp.core.domain.*;
import com.erp.core.dto.request.pos.AddCartItemRequest;
import com.erp.core.dto.request.pos.UpdateCartItemRequest;
import com.erp.core.dto.response.pos.*;
import com.erp.core.enums.PrincipalType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final BranchVariantDailyStockRepository stockRepository;

    public CartServiceImpl(CartRepository cartRepository, CartItemRepository itemRepository,
                           CartItemToppingRepository itemToppingRepository, ProductRepository productRepository,
                           ProductVariantRepository variantRepository, ToppingRepository toppingRepository,
                           ProductToppingRepository productToppingRepository,
                           BranchToppingAvailabilityRepository branchToppingAvailabilityRepository,
                           BranchProductAvailabilityRepository availabilityRepository,
                           BranchVariantDailyStockRepository stockRepository) {
        this.cartRepository = cartRepository;
        this.itemRepository = itemRepository;
        this.itemToppingRepository = itemToppingRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.toppingRepository = toppingRepository;
        this.productToppingRepository = productToppingRepository;
        this.branchToppingAvailabilityRepository = branchToppingAvailabilityRepository;
        this.availabilityRepository = availabilityRepository;
        this.stockRepository = stockRepository;
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
            checkStock(request.branchId(), variant.getId(), request.quantity());
        }

        Cart cart = getOrCreateCart(customerId, request.branchId(), request.sessionToken());
        String ice = normalize(request.iceLevel(), "NORMAL");
        String sugar = normalize(request.sugarLevel(), "NORMAL");
        String note = request.note();
        BigDecimal unitPrice = resolveUnitPrice(product, variant, availability);

        String toppingSignature = toppingSignature(request.toppings());
        CartItem item = itemRepository.findByCartIdAndStatusOrderByCreatedAtAsc(cart.getId(), ACTIVE).stream()
                                      .filter(i -> Objects.equals(i.getProductId(), product.getId())
                                          && Objects.equals(i.getVariantId(), request.variantId())
                                          && Objects.equals(i.getIceLevel(), ice)
                                          && Objects.equals(i.getSugarLevel(), sugar)
                                          && Objects.equals(i.getNote(), note)
                                          && Objects.equals(toppingSignature, toppingSignature(i.getId())))
                                      .findFirst().orElse(null);

        int newQuantity = request.quantity();
        if (item != null) {
            newQuantity += item.getQuantity();
            if (variant != null) {
                checkStock(request.branchId(), variant.getId(), newQuantity);
            }
            item.setQuantity(newQuantity);
            item.setUnitPrice(unitPrice);
            item.setTotalPrice(unitPrice.multiply(BigDecimal.valueOf(newQuantity)));
            itemToppingRepository.deleteByCartItemId(item.getId());
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
        CartItem item =
            itemRepository.findById(itemId).orElseThrow(() -> new BaseException(ErrorCode.ORDER_404_CART_NOT_FOUND));
        Cart cart = cartRepository.findById(item.getCartId())
                                  .orElseThrow(() -> new BaseException(ErrorCode.ORDER_404_CART_NOT_FOUND));
        ensureOwner(cart, customerId);
        if (!ACTIVE.equals(item.getStatus())) {
            throw new BaseException(ErrorCode.ORDER_404_CART_NOT_FOUND);
        }
        if (request.quantity() != null) {
            if (item.getVariantId() != null) {
                checkStock(cart.getBranchId(), item.getVariantId(), request.quantity());
            }
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
        CartItem item =
            itemRepository.findById(itemId).orElseThrow(() -> new BaseException(ErrorCode.ORDER_404_CART_NOT_FOUND));
        Cart cart = cartRepository.findById(item.getCartId())
                                  .orElseThrow(() -> new BaseException(ErrorCode.ORDER_404_CART_NOT_FOUND));
        ensureOwner(cart, customerId);
        if (!ACTIVE.equals(item.getStatus())) {
            throw new BaseException(ErrorCode.ORDER_404_CART_NOT_FOUND);
        }
        itemToppingRepository.deleteByCartItemId(item.getId());
        item.setStatus("DELETED");
        itemRepository.save(item);
        recalculate(cart);
        return new CartMutationResponse(cart.getId(), itemId, null, null, cart.getSubtotalAmount());
    }

    private Cart findCart(UUID customerId, UUID branchId, String sessionToken) {
        if (customerId != null) {
            return cartRepository.findByCustomerIdAndBranchIdAndStatus(customerId, branchId, ACTIVE).orElse(null);
        }
        if (sessionToken != null && !sessionToken.isBlank()) {
            return cartRepository.findBySessionTokenAndBranchIdAndStatus(sessionToken, branchId, ACTIVE).orElse(null);
        }
        return null;
    }

    private Cart getOrCreateCart(UUID customerId, UUID branchId, String sessionToken) {
        Cart cart = findCart(customerId, branchId, sessionToken);
        if (cart != null) {
            return cart;
        }
        try {
            Cart c = new Cart();
            c.setCustomerId(customerId);
            c.setBranchId(branchId);
            c.setSessionToken(sessionToken);
            c.setStatus(ACTIVE);
            c.setSubtotalAmount(BigDecimal.ZERO);
            return cartRepository.save(c);
        } catch (Exception e) {
            Cart existing = findCart(customerId, branchId, sessionToken);
            if (existing != null) {
                return existing;
            }
            throw e;
        }
    }

    private void saveToppings(CartItem item, List<AddCartItemRequest.ToppingRequest> toppings,
                               UUID productId, UUID branchId) {
        if (toppings == null) {
            return;
        }
        Map<UUID, Integer> mergedToppings = new LinkedHashMap<>();
        for (AddCartItemRequest.ToppingRequest req : toppings) {
            mergedToppings.merge(req.toppingId(), req.quantity(), Integer::sum);
        }
        for (Map.Entry<UUID, Integer> entry : mergedToppings.entrySet()) {
            UUID toppingId = entry.getKey();
            int quantity = entry.getValue();
            Topping topping = toppingRepository.findById(toppingId)
                                               .orElseThrow(() -> new BaseException(ErrorCode.INVALID_REQUEST,
                                                                                    "Topping không tồn tại."));
            if (!ACTIVE.equals(topping.getStatus())) {
                throw new BaseException(ErrorCode.INVALID_REQUEST, "Topping không còn khả dụng.");
            }
            ProductTopping productTopping = productToppingRepository
                .findByProductIdAndToppingIdAndStatus(productId, toppingId, ACTIVE)
                .orElseThrow(() -> new BaseException(ErrorCode.INVALID_REQUEST,
                                                      "Topping không thuộc sản phẩm."));
            if (quantity > productTopping.getMaxQuantity()) {
                throw new BaseException(ErrorCode.ORDER_400_INVALID_QUANTITY);
            }
            branchToppingAvailabilityRepository
                .findByBranchIdAndToppingIdAndStatus(branchId, toppingId, ACTIVE)
                .filter(BranchToppingAvailability::isAvailable)
                .orElseThrow(() -> new BaseException(ErrorCode.INVALID_REQUEST,
                                                      "Topping không khả dụng tại chi nhánh."));
            CartItemTopping ct = new CartItemTopping();
            ct.setCartItemId(item.getId());
            ct.setToppingId(topping.getId());
            ct.setQuantity(quantity);
            ct.setUnitPrice(topping.getPrice());
            ct.setTotalPrice(topping.getPrice().multiply(BigDecimal.valueOf(quantity)));
            ct.setStatus(ACTIVE);
            itemToppingRepository.save(ct);
        }
    }

    private BigDecimal resolveUnitPrice(Product p, ProductVariant v, BranchProductAvailability a) {
        BigDecimal base = a.getSalePrice() != null ? a.getSalePrice() : p.getBasePrice();
        return base.add(v == null || v.getPriceDelta() == null ? BigDecimal.ZERO : v.getPriceDelta());
    }

    private void recalculate(Cart cart) {
        BigDecimal subtotal = itemRepository.findByCartIdAndStatusOrderByCreatedAtAsc(cart.getId(), ACTIVE).stream()
                                            .map(i -> i.getTotalPrice().add(
                                                itemToppingRepository.findByCartItemIdAndStatus(i.getId(), ACTIVE)
                                                                     .stream()
                                                                     .map(CartItemTopping::getTotalPrice)
                                                                     .reduce(BigDecimal.ZERO, BigDecimal::add)))
                                            .reduce(BigDecimal.ZERO, BigDecimal::add);
        cart.setSubtotalAmount(subtotal);
        cartRepository.save(cart);
    }

    private String toppingSignature(List<AddCartItemRequest.ToppingRequest> toppings) {
        if (toppings == null || toppings.isEmpty()) {
            return "";
        }
        return toppings.stream()
                       .map(t -> t.toppingId() + ":" + t.quantity())
                       .sorted()
                       .collect(java.util.stream.Collectors.joining(","));
    }

    private String toppingSignature(UUID cartItemId) {
        return itemToppingRepository.findByCartItemIdAndStatus(cartItemId, ACTIVE).stream()
                                     .map(t -> t.getToppingId() + ":" + t.getQuantity())
                                     .sorted()
                                     .collect(java.util.stream.Collectors.joining(","));
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItem> cartItems = itemRepository.findByCartIdAndStatusOrderByCreatedAtAsc(cart.getId(), ACTIVE);

        Set<UUID> productIds = cartItems.stream().map(CartItem::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
                                                          .collect(Collectors.toMap(Product::getId, p -> p));

        Set<UUID> variantIds = cartItems.stream().map(CartItem::getVariantId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, ProductVariant> variantMap = variantIds.isEmpty() ? Map.of() :
            variantRepository.findAllById(variantIds).stream()
                              .collect(Collectors.toMap(ProductVariant::getId, v -> v));

        Map<UUID, List<CartItemTopping>> toppingsByItem = cartItems.stream()
            .collect(Collectors.toMap(CartItem::getId,
                i -> itemToppingRepository.findByCartItemIdAndStatus(i.getId(), ACTIVE)));

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
            return new CartItemResponse(i.getId(), i.getProductId(), p == null ? null : p.getCode(),
                                        p == null ? null : p.getName(),
                                        i.getVariantId(), v == null ? null : v.getVariantName(), i.getQuantity(),
                                        i.getIceLevel(), i.getSugarLevel(), i.getNote(), i.getUnitPrice(),
                                        i.getTotalPrice(), tops);
        }).toList();
        return new CartResponse(cart.getId(), cart.getBranchId(), cart.getSubtotalAmount(), items);
    }

    private void checkStock(UUID branchId, UUID variantId, int quantity) {
        BranchVariantDailyStock stock =
            stockRepository.findByBranchIdAndVariantIdAndBusinessDateAndStatus(branchId, variantId, LocalDate.now(),
                                                                               ACTIVE).orElseThrow(
                () -> new BaseException(ErrorCode.ORDER_400_INVALID_QUANTITY));
        if (quantity > stock.getRemainingQuantity()) {
            throw new BaseException(ErrorCode.ORDER_400_INVALID_QUANTITY);
        }
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
