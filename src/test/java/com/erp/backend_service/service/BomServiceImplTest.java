package com.erp.backend_service.service;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.BomMapper;
import com.erp.backend_service.repository.*;
import com.erp.backend_service.service.impl.BomServiceImpl;
import com.erp.core.domain.*;
import com.erp.core.dto.request.menu.AddBomItemRequest;
import com.erp.core.dto.request.menu.BulkSyncBomRequest;
import com.erp.core.dto.request.menu.UpdateBomItemRequest;
import com.erp.core.dto.response.menu.BomResponse;
import com.erp.core.dto.response.menu.ProductRecipeItemResponse;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BomServiceImplTest {

    @Mock
    private ProductRecipeItemRepository productRecipeItemRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private UnitRepository unitRepository;

    private BomMapper bomMapper;
    private BomServiceImpl bomService;

    private UUID variantId;
    private UUID productId;
    private UUID materialId;
    private UUID unitId;
    private ProductVariant variant;
    private Product product;
    private Material material;
    private Unit unit;

    @BeforeEach
    void setUp() {
        bomMapper = new BomMapper();
        bomService = new BomServiceImpl(
                productRecipeItemRepository,
                productVariantRepository,
                productRepository,
                categoryRepository,
                materialRepository,
                unitRepository,
                bomMapper
        );

        variantId = UUID.randomUUID();
        productId = UUID.randomUUID();
        materialId = UUID.randomUUID();
        unitId = UUID.randomUUID();

        product = new Product();
        product.setId(productId);
        product.setName("Phin Sữa Đá");
        product.setCode("CF-PHIN-SUA");

        variant = new ProductVariant();
        variant.setId(variantId);
        variant.setProductId(productId);
        variant.setVariantName("Size M");
        variant.setVariantCode("CF-PHIN-SUA-M");

        material = new Material();
        material.setId(materialId);
        material.setCode("MAT-MILK-01");
        material.setName("Sữa đặc");

        unit = new Unit();
        unit.setId(unitId);
        unit.setCode("ML");
        unit.setName("Mililit");
    }

    @Test
    @DisplayName("Xem BOM thành công - chỉ trả về các item ACTIVE")
    void testGetBomByVariantId_Success() {
        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        ProductRecipeItem item = new ProductRecipeItem();
        item.setId(UUID.randomUUID());
        item.setVariantId(variantId);
        item.setMaterialId(materialId);
        item.setQuantity(new BigDecimal("30.000"));
        item.setUnitId(unitId);
        item.setWastagePercent(new BigDecimal("2.00"));
        item.setStatus("ACTIVE");

        when(productRecipeItemRepository.findByVariantIdAndStatusOrderByCreatedAtAsc(variantId, "ACTIVE"))
                .thenReturn(List.of(item));
        when(materialRepository.findAllById(any())).thenReturn(List.of(material));
        when(unitRepository.findAllById(any())).thenReturn(List.of(unit));

        BomResponse response = bomService.getBomByVariantId(variantId);

        assertNotNull(response);
        assertEquals(variantId.toString(), response.variantId());
        assertEquals("Phin Sữa Đá - Size M", response.variantName());
        assertEquals(1, response.items().size());
        assertEquals("Sữa đặc", response.items().get(0).materialName());
        assertEquals("ML", response.items().get(0).unitCode());
    }

    @Test
    @DisplayName("Thêm NVL mới vào BOM thành công")
    void testAddItem_NewItem_Success() {
        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));
        when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(productRecipeItemRepository.findByVariantIdAndMaterialId(variantId, materialId))
                .thenReturn(Optional.empty());

        when(productRecipeItemRepository.save(any(ProductRecipeItem.class)))
                .thenAnswer(inv -> {
                    ProductRecipeItem it = inv.getArgument(0);
                    it.setId(UUID.randomUUID());
                    return it;
                });

        AddBomItemRequest request = new AddBomItemRequest(
                materialId,
                new BigDecimal("30.0"),
                unitId,
                new BigDecimal("2.0")
        );

        ProductRecipeItemResponse response = bomService.addItem(variantId, request);

        assertNotNull(response);
        assertEquals("ACTIVE", response.status());
        assertEquals("Sữa đặc", response.materialName());
        assertEquals("ML", response.unitCode());
        assertEquals(new BigDecimal("30.0"), response.quantity());
    }

    @Test
    @DisplayName("Thêm NVL đã bị xóa mềm trước đó -> Tái kích hoạt (Reactivate) thành công")
    void testAddItem_ReactivateSoftDeleted_Success() {
        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));
        when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));

        ProductRecipeItem inactiveItem = new ProductRecipeItem();
        inactiveItem.setId(UUID.randomUUID());
        inactiveItem.setVariantId(variantId);
        inactiveItem.setMaterialId(materialId);
        inactiveItem.setStatus("INACTIVE"); // đã xóa mềm

        when(productRecipeItemRepository.findByVariantIdAndMaterialId(variantId, materialId))
                .thenReturn(Optional.of(inactiveItem));

        when(productRecipeItemRepository.save(any(ProductRecipeItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AddBomItemRequest request = new AddBomItemRequest(
                materialId,
                new BigDecimal("35.0"),
                unitId,
                new BigDecimal("3.0")
        );

        ProductRecipeItemResponse response = bomService.addItem(variantId, request);

        assertNotNull(response);
        assertEquals("ACTIVE", response.status());
        assertEquals(new BigDecimal("35.0"), response.quantity());
    }

    @Test
    @DisplayName("Thêm NVL đã tồn tại và đang ACTIVE -> Báo lỗi 409 Duplicated")
    void testAddItem_DuplicateActive_ThrowsException() {
        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));
        when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));

        ProductRecipeItem activeItem = new ProductRecipeItem();
        activeItem.setStatus("ACTIVE");
        when(productRecipeItemRepository.findByVariantIdAndMaterialId(variantId, materialId))
                .thenReturn(Optional.of(activeItem));

        AddBomItemRequest request = new AddBomItemRequest(
                materialId,
                new BigDecimal("30.0"),
                unitId,
                new BigDecimal("2.0")
        );

        BaseException ex = assertThrows(BaseException.class, () -> bomService.addItem(variantId, request));
        assertEquals(ErrorCode.MENU_409_BOM_MATERIAL_DUPLICATED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Gỡ dòng BOM thành công - Chuyển trạng thái sang INACTIVE (Xóa mềm)")
    void testRemoveItem_SoftDelete_Success() {
        UUID itemId = UUID.randomUUID();
        ProductRecipeItem item = new ProductRecipeItem();
        item.setId(itemId);
        item.setVariantId(variantId);
        item.setStatus("ACTIVE");

        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(productRecipeItemRepository.findByIdAndVariantId(itemId, variantId)).thenReturn(Optional.of(item));

        bomService.removeItem(variantId, itemId);

        assertEquals("INACTIVE", item.getStatus());
        verify(productRecipeItemRepository).save(item);
    }

    @Test
    @DisplayName("Cập nhật toàn bộ BOM (Sync) - Xóa mềm item không còn trong request và cập nhật item có mặt")
    void testSyncBom_Success() {
        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        UUID oldMaterialId = UUID.randomUUID();
        ProductRecipeItem item1 = new ProductRecipeItem();
        item1.setId(UUID.randomUUID());
        item1.setVariantId(variantId);
        item1.setMaterialId(oldMaterialId);
        item1.setStatus("ACTIVE");

        ProductRecipeItem item2 = new ProductRecipeItem();
        item2.setId(UUID.randomUUID());
        item2.setVariantId(variantId);
        item2.setMaterialId(materialId);
        item2.setStatus("ACTIVE");

        when(productRecipeItemRepository.findByVariantId(variantId)).thenReturn(List.of(item1, item2));
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));
        when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));

        BulkSyncBomRequest syncRequest = new BulkSyncBomRequest(List.of(
                new BulkSyncBomRequest.SyncBomItemEntry(
                        item2.getId(),
                        materialId,
                        new BigDecimal("40.0"),
                        unitId,
                        new BigDecimal("1.5")
                )
        ));

        when(productRecipeItemRepository.findByVariantIdAndStatusOrderByCreatedAtAsc(variantId, "ACTIVE"))
                .thenReturn(List.of(item2));
        when(materialRepository.findAllById(any())).thenReturn(List.of(material));
        when(unitRepository.findAllById(any())).thenReturn(List.of(unit));

        BomResponse response = bomService.syncBom(variantId, syncRequest);

        assertEquals("INACTIVE", item1.getStatus()); // Đã bị xóa mềm
        assertEquals("ACTIVE", item2.getStatus());
        assertEquals(new BigDecimal("40.0"), item2.getQuantity());
        assertNotNull(response);
    }
}
