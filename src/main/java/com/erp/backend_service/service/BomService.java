package com.erp.backend_service.service;

import com.erp.core.dto.request.menu.AddBomItemRequest;
import com.erp.core.dto.request.menu.BulkSyncBomRequest;
import com.erp.core.dto.request.menu.UpdateBomItemRequest;
import com.erp.core.dto.response.menu.BomResponse;
import com.erp.core.dto.response.menu.ProductBomOverviewResponse;
import com.erp.core.dto.response.menu.ProductRecipeItemResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service quản lý công thức định lượng (BOM - Bill of Materials) cho các biến thể sản phẩm.
 */
public interface BomService {

    /**
     * Lấy toàn bộ công thức định lượng (các dòng NVL ACTIVE) của một biến thể.
     */
    BomResponse getBomByVariantId(UUID variantId);

    /**
     * Thêm một dòng nguyên vật liệu vào công thức định lượng của biến thể.
     */
    ProductRecipeItemResponse addItem(UUID variantId, AddBomItemRequest request);

    /**
     * Cập nhật một dòng nguyên vật liệu trong công thức định lượng.
     */
    ProductRecipeItemResponse updateItem(UUID variantId, UUID itemId, UpdateBomItemRequest request);

    /**
     * Gỡ (xóa mềm) một dòng nguyên vật liệu khỏi công thức định lượng (chuyển sang INACTIVE).
     */
    void removeItem(UUID variantId, UUID itemId);

    /**
     * Cập nhật / đồng bộ toàn bộ công thức định lượng của một biến thể (thêm mới, cập nhật, xóa mềm).
     */
    BomResponse syncBom(UUID variantId, BulkSyncBomRequest request);

    /**
     * Lấy danh sách tổng quan các biến thể kèm thông tin BOM phục vụ hiển thị màn hình danh sách BOM.
     */
    List<ProductBomOverviewResponse> getBomOverview(String search);
}
