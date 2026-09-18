package com.erp.backend_service.service;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.MaterialRepository;
import com.erp.backend_service.repository.MaterialStockBalanceRepository;
import com.erp.backend_service.repository.WarehouseRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.impl.StockBalanceServiceImpl;
import com.erp.core.domain.Material;
import com.erp.core.domain.MaterialStockBalance;
import com.erp.core.domain.Warehouse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.inv.StockBalanceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockBalanceServiceImplTest {

    @Mock
    private MaterialStockBalanceRepository balanceRepository;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private DataScopeHelper dataScopeHelper;

    private StockBalanceServiceImpl stockBalanceService;

    private UUID warehouseId;
    private UUID materialId;

    @BeforeEach
    void setUp() {
        stockBalanceService = new StockBalanceServiceImpl(
                balanceRepository,
                materialRepository,
                warehouseRepository,
                dataScopeHelper
        );
        warehouseId = UUID.randomUUID();
        materialId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Lọc tồn kho: page âm -> 400 INV_400_INVALID_PAGE, không truy vấn")
    void testList_Fail_PageNegative() {
        BaseException ex = assertThrows(BaseException.class,
                () -> stockBalanceService.list(-1, 20, null, null, null));
        assertEquals(ErrorCode.INV_400_INVALID_PAGE, ex.getErrorCode());
        verify(balanceRepository, never()).searchPaged(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Lọc tồn kho: size = 0 -> 400 INV_400_INVALID_SIZE")
    void testList_Fail_SizeZero() {
        BaseException ex = assertThrows(BaseException.class,
                () -> stockBalanceService.list(0, 0, null, null, null));
        assertEquals(ErrorCode.INV_400_INVALID_SIZE, ex.getErrorCode());
    }

    @Test
    @DisplayName("Lọc tồn kho: size âm -> 400 INV_400_INVALID_SIZE")
    void testList_Fail_SizeNegative() {
        BaseException ex = assertThrows(BaseException.class,
                () -> stockBalanceService.list(0, -5, null, null, null));
        assertEquals(ErrorCode.INV_400_INVALID_SIZE, ex.getErrorCode());
    }

    @Test
    @DisplayName("page = 0 hợp lệ (biên dưới): không ném lỗi")
    void testList_Success_ZeroPageBoundary() {
        when(dataScopeHelper.getAllowedWarehouseIds(null)).thenReturn(List.of());

        PageResponse<StockBalanceResponse> result =
                stockBalanceService.list(0, 20, null, null, null);

        assertNotNull(result);
        assertEquals(0, result.totalElements());
        assertEquals(0, result.content().size());
    }

    @Test
    @DisplayName("Lọc theo warehouseId không tồn tại -> 404 INV_404_WAREHOUSE_NOT_FOUND")
    void testList_Fail_WarehouseNotFound() {
        when(warehouseRepository.existsById(warehouseId)).thenReturn(false);

        BaseException ex = assertThrows(BaseException.class,
                () -> stockBalanceService.list(0, 20, warehouseId, null, null));
        assertEquals(ErrorCode.INV_404_WAREHOUSE_NOT_FOUND, ex.getErrorCode());
        verify(balanceRepository, never()).searchPaged(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Lọc theo materialId không tồn tại -> 404 INV_404_MATERIAL_NOT_FOUND")
    void testList_Fail_MaterialNotFound() {
        when(materialRepository.existsById(materialId)).thenReturn(false);

        BaseException ex = assertThrows(BaseException.class,
                () -> stockBalanceService.list(0, 20, null, materialId, null));
        assertEquals(ErrorCode.INV_404_MATERIAL_NOT_FOUND, ex.getErrorCode());
        verify(balanceRepository, never()).searchPaged(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Filter hợp lệ: trả về đúng bản ghi theo warehouseId + materialId")
    void testList_Success() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setCode("WH-001");
        warehouse.setName("Kho Trung Tâm");

        Material material = new Material();
        material.setId(materialId);
        material.setCode("NL-001");
        material.setName("Bột mì");

        MaterialStockBalance balance = new MaterialStockBalance();
        balance.setWarehouseId(warehouseId);
        balance.setMaterialId(materialId);
        balance.setQuantityOnHand(new BigDecimal("10"));
        balance.setQuantityReserved(new BigDecimal("2"));

        when(warehouseRepository.existsById(warehouseId)).thenReturn(true);
        when(materialRepository.existsById(materialId)).thenReturn(true);
        when(dataScopeHelper.getAllowedWarehouseIds(warehouseId)).thenReturn(List.of(warehouseId));
        when(balanceRepository.searchPaged(eq(warehouseId), eq(materialId), eq(List.of(warehouseId)), any()))
                .thenReturn(new PageImpl<>(List.of(balance), PageRequest.of(0, 20), 1L));
        when(warehouseRepository.findAllById(List.of(warehouseId))).thenReturn(List.of(warehouse));
        when(materialRepository.findAllById(List.of(materialId))).thenReturn(List.of(material));

        PageResponse<StockBalanceResponse> result =
                stockBalanceService.list(0, 20, warehouseId, materialId, null);

        assertNotNull(result);
        assertEquals(1, result.totalElements());
        assertEquals(1, result.content().size());
        StockBalanceResponse item = result.content().get(0);
        assertEquals("WH-001", item.warehouseCode());
        assertEquals("NL-001", item.materialCode());
        assertEquals(new BigDecimal("8"), item.availableQuantity());
    }
}