package com.erp.backend_service.service;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.*;
import com.erp.backend_service.security.CustomUserDetails;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.impl.OrderServiceImpl;
import com.erp.backend_service.service.pos.PosBranchOpenService;
import com.erp.backend_service.service.pos.PosCogsService;
import com.erp.backend_service.service.pos.PosComboService;
import com.erp.backend_service.service.pos.PosIdempotencyService;
import com.erp.backend_service.service.pos.PosShipperAssignService;
import com.erp.core.domain.*;
import com.erp.core.dto.request.pos.CreateOrderRequest;
import com.erp.core.enums.PrincipalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPickupSlotTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository itemRepository;
    @Mock private OrderItemToppingRepository itemToppingRepository;
    @Mock private OrderStatusHistoryRepository historyRepository;
    @Mock private OrderDeliveryRepository deliveryRepository;
    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private CartItemToppingRepository cartItemToppingRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private ToppingRepository toppingRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private BranchProductAvailabilityRepository availabilityRepository;
    @Mock private VoucherRepository voucherRepository;
    @Mock private VoucherUsageRepository voucherUsageRepository;
    @Mock private VoucherBranchRepository voucherBranchRepository;
    @Mock private DataScopeHelper dataScopeHelper;
    @Mock private PosComboService posComboService;
    @Mock private PosCogsService posCogsService;
    @Mock private RefundRepository refundRepository;
    @Mock private PosIdempotencyService posIdempotencyService;
    @Mock private PosBranchOpenService posBranchOpenService;
    @Mock private PosShipperAssignService posShipperAssignService;
    @Mock private PickupTimeSlotRepository pickupTimeSlotRepository;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private OrderServiceImpl orderService;
    private UUID branchId;
    private UUID customerId;
    private UUID slotId;
    private Branch branch;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(
                orderRepository, itemRepository, itemToppingRepository, historyRepository,
                deliveryRepository, cartRepository, cartItemRepository, cartItemToppingRepository,
                productRepository, variantRepository, toppingRepository, customerRepository,
                branchRepository, availabilityRepository, voucherRepository, voucherUsageRepository,
                voucherBranchRepository, dataScopeHelper, posComboService, posCogsService,
                refundRepository, posIdempotencyService, posBranchOpenService,
                posShipperAssignService, pickupTimeSlotRepository, eventPublisher
        );

        branchId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        slotId = UUID.randomUUID();

        branch = new Branch();
        branch.setId(branchId);
        branch.setStatus("ACTIVE");
        branch.setSupportsPickup(true);
        branch.setTimezone("Asia/Ho_Chi_Minh");

        lenient().when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));

        // Mock Security Context for customer
        CustomUserDetails userDetails = new CustomUserDetails(
                PrincipalType.CUSTOMER,
                customerId,
                "customer",
                "pass",
                true,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                java.time.Instant.now()
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }

    @Test
    @DisplayName("Test Case 3 theo SRS: Chặn đặt đơn khi Khung giờ Pickup đã đạt max_orders (ORDER_400_SLOT_FULL)")
    void testOrderFailsWhenSlotIsFull() {
        UUID cartId = UUID.randomUUID();
        Cart cart = new Cart();
        cart.setId(cartId);
        cart.setCustomerId(customerId);
        cart.setBranchId(branchId);

        CartItem cartItem = new CartItem();
        cartItem.setId(UUID.randomUUID());
        cartItem.setCartId(cartId);
        cartItem.setProductId(UUID.randomUUID());
        cartItem.setQuantity(1);
        cartItem.setTotalPrice(BigDecimal.valueOf(30000));

        Product product = new Product();
        product.setId(cartItem.getProductId());
        product.setStatus("ACTIVE");

        BranchProductAvailability bpa = new BranchProductAvailability();
        bpa.setAvailable(true);

        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setFullName("Nguyễn Văn A");
        customer.setPhone("0987654321");
        customer.setEmail("a@test.com");

        when(cartRepository.findByCustomerIdAndBranchIdAndStatus(customerId, branchId, "ACTIVE"))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndStatusOrderByCreatedAtAsc(cartId, "ACTIVE"))
                .thenReturn(List.of(cartItem));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(productRepository.findById(cartItem.getProductId())).thenReturn(Optional.of(product));
        when(availabilityRepository.findByBranchIdAndProductIdAndStatus(branchId, cartItem.getProductId(), "ACTIVE"))
                .thenReturn(Optional.of(bpa));

        // Pickup Slot có max_orders = 5
        PickupTimeSlot slot = new PickupTimeSlot();
        slot.setId(slotId);
        slot.setBranchId(branchId);
        slot.setSlotCode("SLOT-0900-0930");
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(9, 30));
        slot.setMaxOrders(5);
        slot.setStatus("ACTIVE");

        when(pickupTimeSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.of(slot));
        // Đã có đủ 5 đơn trong ngày
        when(orderRepository.countActiveOrdersInSlotOnDate(eq(branchId), eq(slotId), any(), any()))
                .thenReturn(5L);

        CreateOrderRequest request = new CreateOrderRequest(
                branchId, "PICKUP", null, "Nguyễn Văn A", "0987654321", null,
                "CASH", null, slotId, null
        );

        BaseException ex = assertThrows(BaseException.class, () -> orderService.create(null, request));
        assertEquals(ErrorCode.ORDER_400_SLOT_FULL, ex.getErrorCode());
    }

    @Test
    @DisplayName("Khung giờ pickup không tồn tại ném lỗi PICKUP_SLOT_404_NOT_FOUND")
    void testOrderFailsWhenSlotNotFound() {
        UUID cartId = UUID.randomUUID();
        Cart cart = new Cart();
        cart.setId(cartId);
        cart.setCustomerId(customerId);
        cart.setBranchId(branchId);

        CartItem cartItem = new CartItem();
        cartItem.setId(UUID.randomUUID());
        cartItem.setCartId(cartId);
        cartItem.setProductId(UUID.randomUUID());
        cartItem.setQuantity(1);
        cartItem.setTotalPrice(BigDecimal.valueOf(30000));

        Product product = new Product();
        product.setId(cartItem.getProductId());
        product.setStatus("ACTIVE");

        BranchProductAvailability bpa = new BranchProductAvailability();
        bpa.setAvailable(true);

        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setFullName("Nguyễn Văn A");
        customer.setPhone("0987654321");
        customer.setEmail("a@test.com");

        when(cartRepository.findByCustomerIdAndBranchIdAndStatus(customerId, branchId, "ACTIVE"))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndStatusOrderByCreatedAtAsc(cartId, "ACTIVE"))
                .thenReturn(List.of(cartItem));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(productRepository.findById(cartItem.getProductId())).thenReturn(Optional.of(product));
        when(availabilityRepository.findByBranchIdAndProductIdAndStatus(branchId, cartItem.getProductId(), "ACTIVE"))
                .thenReturn(Optional.of(bpa));

        when(pickupTimeSlotRepository.findByIdForUpdate(slotId)).thenReturn(Optional.empty());

        CreateOrderRequest request = new CreateOrderRequest(
                branchId, "PICKUP", null, "Nguyễn Văn A", "0987654321", null,
                "CASH", null, slotId, null
        );

        BaseException ex = assertThrows(BaseException.class, () -> orderService.create(null, request));
        assertEquals(ErrorCode.PICKUP_SLOT_404_NOT_FOUND, ex.getErrorCode());
    }
}
