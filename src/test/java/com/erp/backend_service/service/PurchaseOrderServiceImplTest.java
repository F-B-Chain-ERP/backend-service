package com.erp.backend_service.service;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.PurchaseOrderItemMapper;
import com.erp.backend_service.mapper.PurchaseOrderMapper;
import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.repository.MaterialRepository;
import com.erp.backend_service.repository.PurchaseOrderItemRepository;
import com.erp.backend_service.repository.PurchaseOrderRepository;
import com.erp.backend_service.repository.SupplierRepository;
import com.erp.backend_service.repository.UnitRepository;
import com.erp.backend_service.repository.WarehouseRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.impl.PurchaseOrderServiceImpl;
import com.erp.core.domain.PurchaseOrder;
import com.erp.core.domain.Supplier;
import com.erp.core.domain.Warehouse;
import com.erp.core.dto.request.proc.CreatePurchaseOrderRequest;
import com.erp.core.dto.request.proc.PurchaseOrderItemRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.proc.PurchaseOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceImplTest {

    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private MaterialRepository materialRepository;
    @Mock private UnitRepository unitRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private PurchaseOrderMapper purchaseOrderMapper;
    @Mock private PurchaseOrderItemMapper purchaseOrderItemMapper;
    @Mock private DataScopeHelper dataScopeHelper;
    @Mock private NotificationService notificationService;

    private PurchaseOrderServiceImpl purchaseOrderService;

    private final UUID branchA = UUID.randomUUID();
    private final UUID warehouseA = UUID.randomUUID();
    private final UUID warehouseB = UUID.randomUUID();
    private final UUID supplierId = UUID.randomUUID();
    private final UUID poId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        purchaseOrderService = new PurchaseOrderServiceImpl(
                purchaseOrderRepository,
                purchaseOrderItemRepository,
                supplierRepository,
                warehouseRepository,
                materialRepository,
                unitRepository,
                accountRepository,
                purchaseOrderMapper,
                purchaseOrderItemMapper,
                dataScopeHelper,
                notificationService
        );
    }

    @Test
    @DisplayName("list should pass allowedWarehouseIds from DataScopeHelper to repository search")
    void testListAppliesAllowedWarehouseIds() {
        when(dataScopeHelper.getAllowedWarehouseIds(null)).thenReturn(List.of(warehouseA));

        Page<PurchaseOrder> emptyPage = new PageImpl<>(List.of());
        when(purchaseOrderRepository.search(isNull(), isNull(), isNull(), isNull(), eq(List.of(warehouseA)), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(emptyPage);

        PageResponse<PurchaseOrderResponse> result = purchaseOrderService.list(
                0, 10, null, null, null, null, null, null);

        assertNotNull(result);
        verify(dataScopeHelper).getAllowedWarehouseIds(null);
        verify(purchaseOrderRepository).search(isNull(), isNull(), isNull(), isNull(), eq(List.of(warehouseA)), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("get should enforce warehouse access")
    void testGetEnforcesWarehouseAccess() {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(poId);
        po.setWarehouseId(warehouseA);
        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));

        doThrow(new BaseException(ErrorCode.CROSS_SCOPE_DENIED))
                .when(dataScopeHelper).enforceWarehouseAccess(warehouseA);

        BaseException ex = assertThrows(BaseException.class, () -> purchaseOrderService.get(poId));
        assertEquals(ErrorCode.CROSS_SCOPE_DENIED, ex.getErrorCode());
        verify(dataScopeHelper).enforceWarehouseAccess(warehouseA);
    }

    @Test
    @DisplayName("create should enforce warehouse access for destination warehouse")
    void testCreateEnforcesWarehouseAccess() {
        Supplier supplier = new Supplier();
        supplier.setId(supplierId);
        supplier.setStatus("ACTIVE");
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));

        doThrow(new BaseException(ErrorCode.CROSS_SCOPE_DENIED))
                .when(dataScopeHelper).enforceWarehouseAccess(warehouseB);

        CreatePurchaseOrderRequest req = new CreatePurchaseOrderRequest(
                "PO-202608-0001",
                supplierId,
                warehouseB,
                LocalDate.now(),
                LocalDate.now().plusDays(3),
                "Note",
                List.of(new PurchaseOrderItemRequest(UUID.randomUUID(), BigDecimal.TEN, UUID.randomUUID(), BigDecimal.valueOf(100)))
        );

        BaseException ex = assertThrows(BaseException.class, () -> purchaseOrderService.create(req));
        assertEquals(ErrorCode.CROSS_SCOPE_DENIED, ex.getErrorCode());
        verify(dataScopeHelper).enforceWarehouseAccess(warehouseB);
    }
}
