package com.erp.backend_service.service;

import com.erp.core.dto.request.menu.CreateProductVariantRequest;
import com.erp.core.dto.request.menu.SyncProductVariantsRequest;
import com.erp.core.dto.request.menu.UpdateProductVariantRequest;
import com.erp.core.dto.response.menu.ProductVariantResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service quản lý các biến thể / kích cỡ của sản phẩm (Size S, M, L...).
 */
public interface ProductVariantService {

    /**
     * Lấy danh sách toàn bộ biến thể của sản phẩm theo thứ tự hiển thị.
     */
    List<ProductVariantResponse> getVariantsByProductId(UUID productId);

    /**
     * Tạo một biến thể mới gắn với sản phẩm.
     */
    ProductVariantResponse create(UUID productId, CreateProductVariantRequest request);

    /**
     * Cập nhật thông tin biến thể theo ID và Product ID.
     */
    ProductVariantResponse update(UUID productId, UUID variantId, UpdateProductVariantRequest request);

    /**
     * Xóa một biến thể (kiểm tra an toàn ràng buộc dữ liệu).
     */
    void delete(UUID productId, UUID variantId);

    /**
     * Đồng bộ danh sách biến thể của sản phẩm (thêm mới, cập nhật, xóa mục bị gỡ).
     */
    List<ProductVariantResponse> syncVariants(UUID productId, SyncProductVariantsRequest request);
}
