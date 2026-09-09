package com.erp.backend_service.service;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.ProductMapper;
import com.erp.backend_service.repository.CategoryRepository;
import com.erp.backend_service.repository.ProductRepository;
import com.erp.backend_service.repository.ProductVariantRepository;
import com.erp.backend_service.service.impl.ProductServiceImpl;
import com.erp.core.domain.Category;
import com.erp.core.domain.Product;
import com.erp.core.domain.ProductVariant;
import com.erp.core.dto.request.menu.CreateProductRequest;
import com.erp.core.dto.request.menu.UpdateProductRequest;
import com.erp.core.dto.response.menu.CreateProductResponse;
import com.erp.core.dto.response.menu.ProductDetailResponse;
import com.erp.core.dto.response.menu.ProductResponse;
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
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    private ProductMapper productMapper;
    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productMapper = new ProductMapper();
        productService = new ProductServiceImpl(
                productRepository,
                categoryRepository,
                productVariantRepository,
                productMapper
        );
    }

    @Test
    @DisplayName("Tạo sản phẩm thành công")
    void testCreateProduct_Success() {
        UUID categoryId = UUID.randomUUID();
        Category category = new Category();
        category.setId(categoryId);
        category.setName("Cà phê");
        category.setStatus("ACTIVE");

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(productRepository.existsByCode("CF-DEN")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        CreateProductRequest request = new CreateProductRequest(
                categoryId, "cf-den", "Cà phê đen", "Mô tả", null,
                BigDecimal.valueOf(25000), false, false, false
        );

        CreateProductResponse response = productService.create(request);

        assertNotNull(response);
        assertEquals("CF-DEN", response.code());
        assertEquals("Cà phê đen", response.name());
        assertEquals("ACTIVE", response.status());
    }

    @Test
    @DisplayName("Cập nhật sản phẩm thành công")
    void testUpdateProduct_Success() {
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        Category category = new Category();
        category.setId(categoryId);
        category.setName("Trà sữa");
        category.setStatus("ACTIVE");

        Product existingProduct = new Product();
        existingProduct.setId(productId);
        existingProduct.setCategoryId(categoryId);
        existingProduct.setCode("TS-TRUYEN-THONG");
        existingProduct.setName("Trà sữa truyền thống");
        existingProduct.setBasePrice(BigDecimal.valueOf(30000));
        existingProduct.setStatus("ACTIVE");

        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProductRequest request = new UpdateProductRequest(
                categoryId, "TS-TRUYEN-THONG", "Trà sữa truyền thống size L", "Thơm ngon",
                "https://minio.com/img.png", BigDecimal.valueOf(35000), 12,
                true, true, false, "0,50,100", "0,50,100", "ACTIVE"
        );

        ProductResponse response = productService.update(productId, request);

        assertNotNull(response);
        assertEquals("Trà sữa truyền thống size L", response.name());
        assertEquals(BigDecimal.valueOf(35000), response.basePrice());
        assertTrue(response.isFeatured());
        assertTrue(response.isBestSeller());
        assertEquals(12, response.preparationMinutes());
        verify(productRepository).save(existingProduct);
    }

    @Test
    @DisplayName("Cập nhật sản phẩm thất bại nếu trùng mã sản phẩm khác")
    void testUpdateProduct_DuplicateCode() {
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        Category category = new Category();
        category.setId(categoryId);
        category.setStatus("ACTIVE");

        Product existingProduct = new Product();
        existingProduct.setId(productId);
        existingProduct.setCode("CF-01");
        existingProduct.setStatus("ACTIVE");

        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(productRepository.existsByCodeAndIdNot("CF-02", productId)).thenReturn(true);

        UpdateProductRequest request = new UpdateProductRequest(
                categoryId, "CF-02", "Cà phê mới", null, null,
                BigDecimal.valueOf(20000), 10, false, false, false,
                null, null, "ACTIVE"
        );

        BaseException ex = assertThrows(BaseException.class, () -> productService.update(productId, request));
        assertEquals(ErrorCode.MENU_409_PRODUCT_CODE_EXISTED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Xóa mềm sản phẩm thành công (chuyển trạng thái sang DELETED)")
    void testDeleteProduct_SoftDelete_Success() {
        UUID productId = UUID.randomUUID();

        Product existingProduct = new Product();
        existingProduct.setId(productId);
        existingProduct.setCode("CF-SUA");
        existingProduct.setStatus("ACTIVE");

        when(productRepository.findById(productId)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.delete(productId);

        assertEquals("DELETED", existingProduct.getStatus());
        verify(productRepository).save(existingProduct);
        verify(productRepository, never()).delete(any());
        verify(productRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Xóa mềm sản phẩm đã bị xóa hoặc không tồn tại sẽ báo lỗi 404")
    void testDeleteProduct_NotFound() {
        UUID productId = UUID.randomUUID();

        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        BaseException ex = assertThrows(BaseException.class, () -> productService.delete(productId));
        assertEquals(ErrorCode.MENU_404_PRODUCT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Lấy chi tiết sản phẩm thành công kèm danh sách variants và danh mục")
    void testGetProductDetail_Success() {
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setCategoryId(categoryId);
        product.setCode("CF-PHIN-SUA");
        product.setName("Phin Sữa Đá");
        product.setImageUrl("http://storage/cf-phin-sua.jpg");
        product.setBasePrice(BigDecimal.valueOf(29000));
        product.setStatus("ACTIVE");

        Category category = new Category();
        category.setId(categoryId);
        category.setName("Cà phê");

        ProductVariant v1 = new ProductVariant();
        v1.setId(UUID.randomUUID());
        v1.setProductId(productId);
        v1.setVariantCode("S");
        v1.setVariantName("Size S");
        v1.setSizeLabel("S");
        v1.setPriceDelta(BigDecimal.ZERO);
        v1.setDisplayOrder(1);
        v1.setStatus("ACTIVE");

        ProductVariant v2 = new ProductVariant();
        v2.setId(UUID.randomUUID());
        v2.setProductId(productId);
        v2.setVariantCode("M");
        v2.setVariantName("Size M");
        v2.setSizeLabel("M");
        v2.setPriceDelta(BigDecimal.valueOf(6000));
        v2.setDisplayOrder(2);
        v2.setStatus("ACTIVE");

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(productVariantRepository.findByProductIdOrderByDisplayOrderAsc(productId)).thenReturn(List.of(v1, v2));

        ProductDetailResponse response = productService.get(productId);

        assertNotNull(response);
        assertEquals("CF-PHIN-SUA", response.code());
        assertEquals("Phin Sữa Đá", response.name());
        assertEquals("Cà phê", response.categoryName());
        assertEquals(2, response.variants().size());
        assertEquals("Size S", response.variants().get(0).variantName());
        assertEquals("Size M", response.variants().get(1).variantName());
        assertEquals(BigDecimal.valueOf(6000), response.variants().get(1).priceDelta());
    }

    @Test
    @DisplayName("Lấy chi tiết cho kênh bán hàng chỉ trả về sản phẩm ACTIVE và variants ACTIVE")
    void testGetProductDetailForSales_Success() {
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setCategoryId(categoryId);
        product.setCode("CF-PHIN-SUA");
        product.setName("Phin Sữa Đá");
        product.setStatus("ACTIVE");

        Category category = new Category();
        category.setId(categoryId);
        category.setName("Cà phê");

        ProductVariant v1 = new ProductVariant();
        v1.setId(UUID.randomUUID());
        v1.setProductId(productId);
        v1.setVariantCode("S");
        v1.setVariantName("Size S");
        v1.setStatus("ACTIVE");

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(productVariantRepository.findByProductIdAndStatusOrderByDisplayOrderAsc(productId, "ACTIVE")).thenReturn(List.of(v1));

        ProductDetailResponse response = productService.getDetailForSales(productId);

        assertNotNull(response);
        assertEquals(1, response.variants().size());
    }

    @Test
    @DisplayName("Lấy chi tiết sản phẩm đã bị xóa hoặc không tồn tại sẽ throw MENU_404_PRODUCT_NOT_FOUND")
    void testGetProductDetail_NotFoundOrDeleted() {
        UUID productId = UUID.randomUUID();

        // Không tìm thấy
        when(productRepository.findById(productId)).thenReturn(Optional.empty());
        assertThrows(BaseException.class, () -> productService.get(productId));

        // Đã bị xóa DELETED
        Product deletedProduct = new Product();
        deletedProduct.setId(productId);
        deletedProduct.setStatus("DELETED");
        when(productRepository.findById(productId)).thenReturn(Optional.of(deletedProduct));
        assertThrows(BaseException.class, () -> productService.get(productId));
    }
}
