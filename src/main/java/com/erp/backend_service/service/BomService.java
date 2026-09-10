package com.erp.backend_service.service;

import com.erp.core.dto.request.menu.CreateBomItemRequest;
import com.erp.core.dto.request.menu.UpdateBomItemRequest;
import com.erp.core.dto.request.menu.UpdateBomRequest;
import com.erp.core.dto.response.menu.BomItemResponse;
import com.erp.core.dto.response.menu.BomResponse;
import com.erp.core.dto.response.menu.UpdateBomResponse;

import java.util.UUID;

/**
 * Service quản lý BOM (định mức nguyên vật liệu pha chế) theo_variant.
 */
public interface BomService {

    /** Lấy toàn bộ BOM của_variant. Trả về danh sách rỗng nếu chưa có BOM. */
    BomResponse getBom(UUID variantId);

    /** Thêm một dòng BOM mới vào_variant. */
    BomItemResponse createItem(UUID variantId, CreateBomItemRequest request);

    /** Cập nhật quantity và wastagePercent của một dòng BOM. */
    BomItemResponse updateItem(UUID itemId, UpdateBomItemRequest request);

    /** Xóa硬(delete) một dòng BOM. */
    void deleteItem(UUID itemId);

    /** Thay thế toàn bộ BOM của_variant (bulk replace). */
    UpdateBomResponse bulkReplace(UUID variantId, UpdateBomRequest request);
}
