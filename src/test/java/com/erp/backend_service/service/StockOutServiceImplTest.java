package com.erp.backend_service.service;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.StockOutItemMapper;
import com.erp.backend_service.mapper.StockOutMapper;
import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.repository.MaterialRepository;
import com.erp.backend_service.repository.MaterialStockBalanceRepository;
import com.erp.backend_service.repository.StockCountRepository;
import com.erp.backend_service.repository.StockOutItemRepository;
import com.erp.backend_service.repository.StockOutRepository;
import com.erp.backend_service.repository.WarehouseRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.impl.StockBalanceMutationService;
import com.erp.backend_service.service.impl.StockOutServiceImpl;
import com.erp.core.domain.StockOut;
import com.erp.core.domain.StockOutItem;
import com.erp.core.dto.request.inv.StatusUpdateRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.inv.StockOutResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

/**
 * Regression test cho SCRUM-100 (TC-Xuất kho):
 * 1) Chức năng chuyển trạng thái phiếu xuất (PATCH /{id}/status) tương tự phiếu nhập.
 * 2) Tìm kiếm theo khoảng ngày: khi truyền thiếu 1 đầu thì dùng mặc định 0001-01-01 / 9999-12-31,
 *    không bind null xuống query như lỗi cũ.
 */
@ExtendWith(MockitoExtension.class)
class StockOutServiceImplTest {

    @Mock private StockOutRepository stockOutRepository;
    @Mock private StockOutItemRepository stockOutItemRepository;
    @Mock private MaterialStockBalanceRepository materialStockBalanceRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private MaterialRepository materialRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private StockCountRepository stockCountRepository;
    @Mock private DataScopeHelper dataScopeHelper;
    @Mock private StockBalanceMutationService balanceMutationService;

    private final StockOutMapper stockOutMapper = new StockOutMapper();
    private final StockOutItemMapper stockOutItemMapper = new StockOutItemMapper();

    private StockOutServiceImpl stockOutService;

    private final UUID stockOutId = UUID.randomUUID();
    private final UUID warehouseId = UUID.randomUUID();
    private final UUID materialId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        stockOutService = new StockOutServiceImpl(
                stockOutRepository,
                stockOutItemRepository,
                materialStockBalanceRepository,
                warehouseRepository,
                materialRepository,
                accountRepository,
                stockCountRepository,
                stockOutMapper,
                stockOutItemMapper,
                dataScopeHelper,
                balanceMutationService);
        lenient().when(dataScopeHelper.enforceWarehouseAccess(any())).thenReturn(null);
    }

    private StockOut draftStockOut() {
        StockOut so = new StockOut();
        so.setId(stockOutId);
        so.setCode("SO-TEST-0001");
        so.setWarehouseId(warehouseId);
        so.setDestinationType("BRANCH_ISSUE");
        so.setOutDate(LocalDate.now());
        so.setStatus("DRAFT");
        return so;
    }

    private StockOutItem item() {
        StockOutItem it = new StockOutItem();
        it.setStockOutId(stockOutId);
        it.setMaterialId(materialId);
        it.setQuantity(BigDecimal.TEN);
        it.setUnitPrice(BigDecimal.ZERO);
        it.setStatus("ACTIVE");
        return it;
    }

    @Test
    @DisplayName("list không truyền ngày -> dùng mặc định 0001-01-01 và 9999-12-31 (không bind null)")
    void testList_NullDatesUseDefaults() {
        when(dataScopeHelper.getAllowedWarehouseIds(null)).thenReturn(null);
        when(stockOutRepository.search(
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(LocalDate.of(1, 1, 1)), eq(LocalDate.of(9999, 12, 31)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<StockOutResponse> result = stockOutService.list(0, 10, null, null, null, null, null, null);

        assertNotNull(result);
        assertEquals(0, result.content().size());
        verify(stockOutRepository).search(
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(LocalDate.of(1, 1, 1)), eq(LocalDate.of(9999, 12, 31)), any(Pageable.class));
    }

    @Test
    @DisplayName("list chỉ truyền fromDate -> toDate mặc định 9999-12-31")
    void testList_OnlyFromDateUsesDefaultToDate() {
        LocalDate fromDate = LocalDate.of(2026, 9, 1);
        when(dataScopeHelper.getAllowedWarehouseIds(null)).thenReturn(null);
        when(stockOutRepository.search(
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(fromDate), eq(LocalDate.of(9999, 12, 31)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        stockOutService.list(0, 10, null, null, null, null, fromDate, null);

        verify(stockOutRepository).search(
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(fromDate), eq(LocalDate.of(9999, 12, 31)), any(Pageable.class));
    }

    @Test
    @DisplayName("list fromDate > toDate -> chặn với INV_400_STOCK_OUT_INVALID_FILTER")
    void testList_FromDateAfterToDateRejected() {
        BaseException ex = assertThrows(BaseException.class,
                () -> stockOutService.list(0, 10, null, null, null, null,
                        LocalDate.of(2026, 9, 18), LocalDate.of(2026, 9, 1)));
        assertEquals(ErrorCode.INV_400_STOCK_OUT_INVALID_FILTER, ex.getErrorCode());
    }

    @Test
    @DisplayName("list truyền allowedWarehouseIds từ DataScopeHelper xuống query khi không chọn kho")
    void testList_AppliesAllowedWarehouseIds() {
        UUID branchWarehouse = UUID.randomUUID();
        when(dataScopeHelper.getAllowedWarehouseIds(null)).thenReturn(List.of(branchWarehouse));
        when(stockOutRepository.search(
                isNull(), isNull(), isNull(), eq(List.of(branchWarehouse)), isNull(),
                any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        stockOutService.list(0, 10, null, null, null, null, null, null);

        verify(stockOutRepository).search(
                isNull(), isNull(), isNull(), eq(List.of(branchWarehouse)), isNull(),
                any(LocalDate.class), any(LocalDate.class), any(Pageable.class));
    }

    @Test
    @DisplayName("changeStatus POSTED (phiếu DRAFT, có dòng) -> trạng thái POSTED, giảm tồn, ghi postedAt")
    void testChangeStatus_PostedHappyPath() {
        when(stockOutRepository.findByIdForUpdate(stockOutId)).thenReturn(Optional.of(draftStockOut()));
        when(stockOutItemRepository.findByStockOutId(stockOutId)).thenReturn(List.of(item()));
        when(stockCountRepository.existsCounting(warehouseId)).thenReturn(false);
        when(stockOutRepository.save(any(StockOut.class))).thenAnswer(inv -> inv.getArgument(0));

        stockOutService.changeStatus(stockOutId, new StatusUpdateRequest("POSTED"));

        ArgumentCaptor<StockOut> captor = ArgumentCaptor.forClass(StockOut.class);
        verify(stockOutRepository).save(captor.capture());
        assertEquals("POSTED", captor.getValue().getStatus());
        assertNotNull(captor.getValue().getPostedAt());
        verify(balanceMutationService).decrease(warehouseId, materialId, BigDecimal.TEN);
    }

    @Test
    @DisplayName("changeStatus on POSTED -> chặn với INV_400_STOCK_OUT_INVALID_STATUS")
    void testChangeStatus_NonDraftRejected() {
        StockOut posted = draftStockOut();
        posted.setStatus("POSTED");
        when(stockOutRepository.findByIdForUpdate(stockOutId)).thenReturn(Optional.of(posted));

        BaseException ex = assertThrows(BaseException.class,
                () -> stockOutService.changeStatus(stockOutId, new StatusUpdateRequest("POSTED")));
        assertEquals(ErrorCode.INV_400_STOCK_OUT_INVALID_STATUS, ex.getErrorCode());
    }

    @Test
    @DisplayName("changeStatus status rỗng/null -> INV_400_STOCK_OUT_INVALID_STATUS")
    void testChangeStatus_NullStatusRejected() {
        when(stockOutRepository.findByIdForUpdate(stockOutId)).thenReturn(Optional.of(draftStockOut()));

        BaseException ex = assertThrows(BaseException.class,
                () -> stockOutService.changeStatus(stockOutId, new StatusUpdateRequest(null)));
        assertEquals(ErrorCode.INV_400_STOCK_OUT_INVALID_STATUS, ex.getErrorCode());
    }

    @Test
    @DisplayName("changeStatus status không hợp lệ -> INV_400_STOCK_OUT_INVALID_STATUS")
    void testChangeStatus_InvalidStatusRejected() {
        when(stockOutRepository.findByIdForUpdate(stockOutId)).thenReturn(Optional.of(draftStockOut()));

        BaseException ex = assertThrows(BaseException.class,
                () -> stockOutService.changeStatus(stockOutId, new StatusUpdateRequest("APPROVED")));
        assertEquals(ErrorCode.INV_400_STOCK_OUT_INVALID_STATUS, ex.getErrorCode());
    }

    @Test
    @DisplayName("changeStatus POSTED nhưng phiếu không có dòng -> INV_400_STOCK_OUT_ITEMS_EMPTY")
    void testChangeStatus_PostedWithoutItemsRejected() {
        when(stockOutRepository.findByIdForUpdate(stockOutId)).thenReturn(Optional.of(draftStockOut()));
        when(stockOutItemRepository.findByStockOutId(stockOutId)).thenReturn(List.of());

        BaseException ex = assertThrows(BaseException.class,
                () -> stockOutService.changeStatus(stockOutId, new StatusUpdateRequest("POSTED")));
        assertEquals(ErrorCode.INV_400_STOCK_OUT_ITEMS_EMPTY, ex.getErrorCode());
    }
}