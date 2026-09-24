package com.erp.backend_service.service.pos;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.MaterialRepository;
import com.erp.backend_service.repository.MaterialStockBalanceRepository;
import com.erp.backend_service.repository.ProductRecipeItemRepository;
import com.erp.backend_service.repository.ProductVariantRepository;
import com.erp.backend_service.repository.WarehouseRepository;
import com.erp.backend_service.service.UnitConversionService;
import com.erp.core.domain.Material;
import com.erp.core.domain.MaterialStockBalance;
import com.erp.core.domain.ProductRecipeItem;
import com.erp.core.domain.ProductVariant;
import com.erp.core.domain.Warehouse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PosCapabilityServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private MaterialStockBalanceRepository balanceRepository;
    @Mock
    private ProductRecipeItemRepository recipeRepository;
    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private ProductVariantRepository variantRepository;
    @Mock
    private UnitConversionService unitConversionService;

    private PosCapabilityService capabilityService;

    private final UUID branchId = UUID.randomUUID();
    private final UUID warehouseId = UUID.randomUUID();
    private final UUID variantId = UUID.randomUUID();
    private final UUID materialId = UUID.randomUUID();
    private final UUID baseUnitId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        capabilityService = new PosCapabilityService(
                warehouseRepository, balanceRepository, recipeRepository,
                materialRepository, variantRepository, unitConversionService);
    }

    private void mockSellingWarehouse() {
        Warehouse wh = new Warehouse();
        wh.setId(warehouseId);
        wh.setBranchId(branchId);
        wh.setStatus("ACTIVE");
        wh.setWarehouseType("BRANCH");
        when(warehouseRepository.findByBranchId(branchId)).thenReturn(List.of(wh));
    }

    private void mockBom(BigDecimal bomQty, BigDecimal onHand) {
        ProductRecipeItem recipe = new ProductRecipeItem();
        recipe.setVariantId(variantId);
        recipe.setMaterialId(materialId);
        recipe.setQuantity(bomQty);
        recipe.setWastagePercent(BigDecimal.ZERO);
        recipe.setUnitId(baseUnitId);
        when(recipeRepository.findByVariantIdInAndStatus(List.of(variantId), "ACTIVE"))
                .thenReturn(List.of(recipe));

        Material material = new Material();
        material.setId(materialId);
        material.setBaseUnitId(baseUnitId);
        when(materialRepository.findAllById(List.of(materialId))).thenReturn(List.of(material));

        MaterialStockBalance balance = new MaterialStockBalance();
        balance.setMaterialId(materialId);
        balance.setQuantityOnHand(onHand);
        balance.setQuantityReserved(BigDecimal.ZERO);
        when(balanceRepository.findByWarehouseId(warehouseId)).thenReturn(List.of(balance));

        when(unitConversionService.convertToBaseUnitLenient(any(), eq(baseUnitId), eq(material)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Đủ NVL: capability = floor(tồn / BOM), checkSaleable cho qua")
    void testEnoughNvl() {
        mockSellingWarehouse();
        // BOM 20g/ly, tồn 100g -> pha được 5 ly.
        mockBom(new BigDecimal("20"), new BigDecimal("100"));

        assertEquals(5, capabilityService.capabilityForVariant(branchId, variantId));
        assertDoesNotThrow(() -> capabilityService.checkSaleable(branchId, variantId, 5));
    }

    @Test
    @DisplayName("Thiếu NVL: checkSaleable ném ORDER_400_INVALID_QUANTITY nêu số ly pha được")
    void testInsufficientNvl() {
        mockSellingWarehouse();
        // BOM 30g/ly, tồn 100g -> pha được 3 ly, xin 4 ly thì chặn.
        mockBom(new BigDecimal("30"), new BigDecimal("100"));

        assertEquals(3, capabilityService.capabilityForVariant(branchId, variantId));
        BaseException ex = assertThrows(BaseException.class,
                () -> capabilityService.checkSaleable(branchId, variantId, 4));
        assertEquals(ErrorCode.ORDER_400_INVALID_QUANTITY, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("3 ly"));
    }

    @Test
    @DisplayName("Món pha chế chưa có BOM: chặn bán và nêu rõ món chưa có công thức")
    void testNoBomBlocksSale() {
        mockSellingWarehouse();
        when(recipeRepository.findByVariantIdInAndStatus(List.of(variantId), "ACTIVE"))
                .thenReturn(List.of());

        ProductVariant variant = new ProductVariant();
        variant.setVariantCode("TS-TRUYEN-THONG");
        variant.setVariantName("Trà sữa truyền thống");
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));

        assertNull(capabilityService.capabilityForVariant(branchId, variantId));
        BaseException ex = assertThrows(BaseException.class,
                () -> capabilityService.checkSaleable(branchId, variantId, 100));
        assertEquals(ErrorCode.ORDER_400_RECIPE_REQUIRED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("TS-TRUYEN-THONG"));
    }

    @Test
    @DisplayName("Chưa có kho bán hàng: capability null, cho qua + không gọi kho tồn")
    void testNoWarehouseAllowsSale() {
        when(warehouseRepository.findByBranchId(branchId)).thenReturn(List.of());
        ProductRecipeItem recipe = new ProductRecipeItem();
        recipe.setVariantId(variantId);
        when(recipeRepository.findByVariantIdInAndStatus(List.of(variantId), "ACTIVE"))
                .thenReturn(List.of(recipe));

        assertNull(capabilityService.capabilityForVariant(branchId, variantId));
        assertDoesNotThrow(() -> capabilityService.checkSaleable(branchId, variantId, 10));
        verifyNoInteractions(balanceRepository);
    }

    @Test
    @DisplayName("variantId null: bỏ qua kiểm tra")
    void testNullVariantSkipped() {
        assertNull(capabilityService.capabilityForVariant(branchId, null));
        assertDoesNotThrow(() -> capabilityService.checkSaleable(branchId, null, 10));
        verifyNoInteractions(warehouseRepository);
    }

    @Test
    @DisplayName("Tên biến thể hiện trong message lỗi thiếu NVL")
    void testVariantLabelInMessage() {
        mockSellingWarehouse();
        mockBom(new BigDecimal("50"), new BigDecimal("100"));

        ProductVariant variant = new ProductVariant();
        variant.setVariantCode("TS-SUA");
        variant.setVariantName("Trà sữa truyền thống");
        when(variantRepository.findById(variantId)).thenReturn(Optional.of(variant));

        BaseException ex = assertThrows(BaseException.class,
                () -> capabilityService.checkSaleable(branchId, variantId, 5));
        assertTrue(ex.getMessage().contains("TS-SUA"));
    }
}
