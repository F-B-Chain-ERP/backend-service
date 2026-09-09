package com.erp.backend_service.service;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.ProductMapper;
import com.erp.backend_service.repository.ProductRepository;
import com.erp.backend_service.repository.ProductVariantRepository;
import com.erp.backend_service.service.impl.ProductVariantServiceImpl;
import com.erp.core.domain.Product;
import com.erp.core.domain.ProductVariant;
import com.erp.core.dto.request.menu.CreateProductVariantRequest;
import com.erp.core.dto.request.menu.SyncProductVariantsRequest;
import com.erp.core.dto.request.menu.UpdateProductVariantRequest;
import com.erp.core.dto.response.menu.ProductVariantResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductVariantServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    private ProductMapper productMapper;
    private ProductVariantServiceImpl productVariantService;

    private UUID productId;
    private Product product;

    @BeforeEach
    void setUp() {
        productMapper = new ProductMapper();
        productVariantService = new ProductVariantServiceImpl(
                productRepository,
                productVariantRepository,
                productMapper
        );

        productId = UUID.randomUUID();
        product = new Product();
        product.setId(productId);
        product.setCode("CF-DEN");
        product.setName("Cà phê đen");
        product.setStatus("ACTIVE");
    }

    @Test
    @DisplayName("Lấy danh sách biến thể của sản phẩm thành công")
    void testGetVariantsByProductId_Success() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        ProductVariant v1 = new ProductVariant();
        v1.setId(UUID.randomUUID());
        v1.setProductId(productId);
        v1.setVariantCode("S");
        v1.setVariantName("Size S");
        v1.setSizeLabel("S");
        v1.setPriceDelta(BigDecimal.ZERO);
        v1.setDisplayOrder(1);
        v1.setStatus("ACTIVE");

        when(productVariantRepository.findByProductIdOrderByDisplayOrderAsc(productId))
                .thenReturn(List.of(v1));

        List<ProductVariantResponse> result = productVariantService.getVariantsByProductId(productId);

        assertEquals(1, result.size());
        assertEquals("S", result.get(0).variantCode());
        assertEquals("Size S", result.get(0).variantName());
    }

    @Test
    @DisplayName("Tạo biến thể thành công")
    void testCreateVariant_Success() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productVariantRepository.existsByProductIdAndVariantCodeIgnoreCase(productId, "L")).thenReturn(false);

        when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(invocation -> {
            ProductVariant pv = invocation.getArgument(0);
            pv.setId(UUID.randomUUID());
            return pv;
        });

        CreateProductVariantRequest request = new CreateProductVariantRequest(
                "L", "Size L", "L", BigDecimal.valueOf(10000), 3
        );

        ProductVariantResponse response = productVariantService.create(productId, request);

        assertNotNull(response);
        assertEquals("L", response.variantCode());
        assertEquals("Size L", response.variantName());
        assertEquals("L", response.sizeLabel());
        assertEquals(BigDecimal.valueOf(10000), response.priceDelta());
        assertEquals(3, response.displayOrder());
        assertEquals("ACTIVE", response.status());
    }

    @Test
    @DisplayName("Tạo biến thể thất bại khi mã biến thể đã tồn tại")
    void testCreateVariant_DuplicateCode_ThrowsConflict() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productVariantRepository.existsByProductIdAndVariantCodeIgnoreCase(productId, "L")).thenReturn(true);

        CreateProductVariantRequest request = new CreateProductVariantRequest(
                "L", "Size L", "L", BigDecimal.valueOf(10000), 3
        );

        BaseException ex = assertThrows(BaseException.class, () -> productVariantService.create(productId, request));
        assertEquals(ErrorCode.MENU_409_VARIANT_CODE_EXISTED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Cập nhật biến thể thành công")
    void testUpdateVariant_Success() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        variant.setProductId(productId);
        variant.setVariantCode("M");
        variant.setVariantName("Size M cũ");
        variant.setSizeLabel("M");
        variant.setPriceDelta(BigDecimal.valueOf(5000));
        variant.setDisplayOrder(2);
        variant.setStatus("ACTIVE");

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productVariantRepository.findByIdAndProductId(variantId, productId)).thenReturn(Optional.of(variant));
        when(productVariantRepository.existsByProductIdAndVariantCodeIgnoreCaseAndIdNot(productId, "M-PLUS", variantId)).thenReturn(false);
        when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProductVariantRequest request = new UpdateProductVariantRequest(
                "M-PLUS", "Size M Lớn", "M+", BigDecimal.valueOf(7000), 2, "ACTIVE"
        );

        ProductVariantResponse updated = productVariantService.update(productId, variantId, request);

        assertEquals("M-PLUS", updated.variantCode());
        assertEquals("Size M Lớn", updated.variantName());
        assertEquals("M+", updated.sizeLabel());
        assertEquals(BigDecimal.valueOf(7000), updated.priceDelta());
    }

    @Test
    @DisplayName("Xóa biến thể thành công")
    void testDeleteVariant_Success() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        variant.setProductId(productId);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productVariantRepository.findByIdAndProductId(variantId, productId)).thenReturn(Optional.of(variant));
        doNothing().when(productVariantRepository).delete(variant);
        doNothing().when(productVariantRepository).flush();

        assertDoesNotThrow(() -> productVariantService.delete(productId, variantId));
        verify(productVariantRepository, times(1)).delete(variant);
    }

    @Test
    @DisplayName("Xóa biến thể ném MENU_400_VARIANT_IN_USE khi vi phạm ràng buộc dữ liệu")
    void testDeleteVariant_InUse_ThrowsException() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        variant.setProductId(productId);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productVariantRepository.findByIdAndProductId(variantId, productId)).thenReturn(Optional.of(variant));
        doThrow(new DataIntegrityViolationException("Foreign key violation"))
                .when(productVariantRepository).flush();

        BaseException ex = assertThrows(BaseException.class, () -> productVariantService.delete(productId, variantId));
        assertEquals(ErrorCode.MENU_400_VARIANT_IN_USE, ex.getErrorCode());
    }

    @Test
    @DisplayName("Đồng bộ biến thể thành công")
    void testSyncVariants_Success() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        UUID existingId = UUID.randomUUID();
        ProductVariant existing = new ProductVariant();
        existing.setId(existingId);
        existing.setProductId(productId);
        existing.setVariantCode("S");
        existing.setVariantName("Size S");
        existing.setSizeLabel("S");
        existing.setPriceDelta(BigDecimal.ZERO);

        when(productVariantRepository.findByProductIdOrderByDisplayOrderAsc(productId))
                .thenReturn(new ArrayList<>(List.of(existing)));

        SyncProductVariantsRequest request = new SyncProductVariantsRequest(List.of(
                new SyncProductVariantsRequest.VariantItemRequest(
                        existingId, "S", "Size S Cập nhật", "S", BigDecimal.ZERO, 1, "ACTIVE"
                ),
                new SyncProductVariantsRequest.VariantItemRequest(
                        null, "M", "Size M Mới", "M", BigDecimal.valueOf(5000), 2, "ACTIVE"
                )
        ));

        List<ProductVariantResponse> result = productVariantService.syncVariants(productId, request);
        assertNotNull(result);
        verify(productVariantRepository, atLeastOnce()).save(any(ProductVariant.class));
    }
}
