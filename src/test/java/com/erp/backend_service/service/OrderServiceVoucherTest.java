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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class OrderServiceVoucherTest {

    private static final String EXPECTED_VOUCHER_MSG = "Mã giảm giá không hợp lệ hoặc không áp dụng cho đơn hàng này.";

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
    private UUID cartId;
    private Branch branch;
    private Customer customer;
    private Cart cart;
    private CartItem cartItem;
    private Product product;
    private BranchProductAvailability bpa;

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
        cartId = UUID.randomUUID();

        branch = new Branch();
        branch.setId(branchId);
        branch.setStatus("ACTIVE");
        branch.setSupportsPickup(true);
        branch.setTimezone("Asia/Ho_Chi_Minh");

        customer = new Customer();
        customer.setId(customerId);
        customer.setFullName("Test Customer");
        customer.setPhone("0987654321");
        customer.setEmail("customer@test.com");

        cart = new Cart();
        cart.setId(cartId);
        cart.setCustomerId(customerId);
        cart.setBranchId(branchId);
        cart.setStatus("ACTIVE");

        cartItem = new CartItem();
        cartItem.setId(UUID.randomUUID());
        cartItem.setCartId(cartId);
        cartItem.setProductId(UUID.randomUUID());
        cartItem.setQuantity(1);
        cartItem.setTotalPrice(BigDecimal.valueOf(50000));
        cartItem.setStatus("ACTIVE");

        product = new Product();
        product.setId(cartItem.getProductId());
        product.setStatus("ACTIVE");

        bpa = new BranchProductAvailability();
        bpa.setAvailable(true);

        lenient().when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));
        lenient().when(cartRepository.findByCustomerIdAndBranchIdAndStatus(customerId, branchId, "ACTIVE"))
                .thenReturn(Optional.of(cart));
        lenient().when(cartItemRepository.findByCartIdAndStatusOrderByCreatedAtAsc(cartId, "ACTIVE"))
                .thenReturn(List.of(cartItem));
        lenient().when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        lenient().when(productRepository.findById(cartItem.getProductId())).thenReturn(Optional.of(product));
        lenient().when(availabilityRepository.findByBranchIdAndProductIdAndStatus(branchId, cartItem.getProductId(), "ACTIVE"))
                .thenReturn(Optional.of(bpa));

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
                Instant.now()
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Voucher không tồn tại ném lỗi thông điệp chuẩn hóa")
    void voucherNotFound_ThrowsUnifiedMessage() {
        when(voucherRepository.findByCodeIgnoreCaseAndStatus("UNKNOWN", "ACTIVE"))
                .thenReturn(Optional.empty());

        CreateOrderRequest request = new CreateOrderRequest(
                branchId, "PICKUP", "UNKNOWN", "Test Customer", "0987654321", null,
                "CASH", null, null, null
        );

        BaseException ex = assertThrows(BaseException.class, () -> orderService.create(null, request));
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
        assertEquals(EXPECTED_VOUCHER_MSG, ex.getMessage());
    }

    @Test
    @DisplayName("Voucher không áp dụng tại chi nhánh ném lỗi thông điệp chuẩn hóa")
    void voucherBranchMismatch_ThrowsUnifiedMessage() {
        UUID voucherId = UUID.randomUUID();
        Voucher voucher = new Voucher();
        voucher.setId(voucherId);
        voucher.setCode("DISCOUNT10");
        voucher.setStatus("ACTIVE");

        when(voucherRepository.findByCodeIgnoreCaseAndStatus("DISCOUNT10", "ACTIVE"))
                .thenReturn(Optional.of(voucher));
        when(voucherBranchRepository.findByVoucherIdAndBranchIdAndStatus(voucherId, branchId, "ACTIVE"))
                .thenReturn(Optional.empty());

        CreateOrderRequest request = new CreateOrderRequest(
                branchId, "PICKUP", "DISCOUNT10", "Test Customer", "0987654321", null,
                "CASH", null, null, null
        );

        BaseException ex = assertThrows(BaseException.class, () -> orderService.create(null, request));
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
        assertEquals(EXPECTED_VOUCHER_MSG, ex.getMessage());
    }

    @Test
    @DisplayName("Voucher hết hạn ném lỗi thông điệp chuẩn hóa")
    void voucherExpired_ThrowsUnifiedMessage() {
        UUID voucherId = UUID.randomUUID();
        Voucher voucher = new Voucher();
        voucher.setId(voucherId);
        voucher.setCode("EXPIRED");
        voucher.setStatus("ACTIVE");
        voucher.setStartAt(Instant.now().minus(10, ChronoUnit.DAYS));
        voucher.setEndAt(Instant.now().minus(1, ChronoUnit.DAYS));

        VoucherBranch vb = new VoucherBranch();
        vb.setVoucherId(voucherId);
        vb.setBranchId(branchId);

        when(voucherRepository.findByCodeIgnoreCaseAndStatus("EXPIRED", "ACTIVE"))
                .thenReturn(Optional.of(voucher));
        when(voucherBranchRepository.findByVoucherIdAndBranchIdAndStatus(voucherId, branchId, "ACTIVE"))
                .thenReturn(Optional.of(vb));

        CreateOrderRequest request = new CreateOrderRequest(
                branchId, "PICKUP", "EXPIRED", "Test Customer", "0987654321", null,
                "CASH", null, null, null
        );

        BaseException ex = assertThrows(BaseException.class, () -> orderService.create(null, request));
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
        assertEquals(EXPECTED_VOUCHER_MSG, ex.getMessage());
    }

    @Test
    @DisplayName("Đơn hàng chưa đạt giá trị tối thiểu của voucher ném lỗi thông điệp chuẩn hóa")
    void voucherMinOrderAmount_ThrowsUnifiedMessage() {
        UUID voucherId = UUID.randomUUID();
        Voucher voucher = new Voucher();
        voucher.setId(voucherId);
        voucher.setCode("BIGORDER");
        voucher.setStatus("ACTIVE");
        voucher.setStartAt(Instant.now().minus(1, ChronoUnit.DAYS));
        voucher.setEndAt(Instant.now().plus(1, ChronoUnit.DAYS));
        voucher.setMinOrderAmount(BigDecimal.valueOf(200000)); // Cart item chỉ 50000

        VoucherBranch vb = new VoucherBranch();
        vb.setVoucherId(voucherId);
        vb.setBranchId(branchId);

        when(voucherRepository.findByCodeIgnoreCaseAndStatus("BIGORDER", "ACTIVE"))
                .thenReturn(Optional.of(voucher));
        when(voucherBranchRepository.findByVoucherIdAndBranchIdAndStatus(voucherId, branchId, "ACTIVE"))
                .thenReturn(Optional.of(vb));

        CreateOrderRequest request = new CreateOrderRequest(
                branchId, "PICKUP", "BIGORDER", "Test Customer", "0987654321", null,
                "CASH", null, null, null
        );

        BaseException ex = assertThrows(BaseException.class, () -> orderService.create(null, request));
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
        assertEquals(EXPECTED_VOUCHER_MSG, ex.getMessage());
    }
}
