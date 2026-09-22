package com.erp.backend_service.controller;

import com.erp.backend_service.service.BomService;
import com.erp.core.dto.request.menu.AddBomItemRequest;
import com.erp.core.dto.request.menu.BulkSyncBomRequest;
import com.erp.core.dto.request.menu.UpdateBomItemRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.BomResponse;
import com.erp.core.dto.response.menu.ProductBomOverviewResponse;
import com.erp.core.dto.response.menu.ProductRecipeItemResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller quản lý công thức định lượng pha chế (BOM - Bill of Materials).
 */
@RestController
@RequestMapping("/api/v1/menu")
@Validated
public class BomController {

    private final BomService bomService;

    private static final Logger log = LoggerFactory.getLogger(BomController.class);

    public BomController(BomService bomService) {
        this.bomService = bomService;
    }

    /**
     * 1. Xem công thức định lượng (BOM) của một biến thể đồ uống.
     */
    @GetMapping("/variants/{variantId}/bom")
    @PreAuthorize("hasAuthority('menu:bom:view')")
    public ResponseEntity<ApiResponse<BomResponse>> getBom(@PathVariable UUID variantId) {
        log.info("Get BOM: variantId={}", variantId);
        BomResponse response = bomService.getBomByVariantId(variantId);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy công thức định lượng thành công"));
    }

    /**
     * 2. Thêm một dòng nguyên vật liệu vào công thức định lượng (BOM) của biến thể.
     */
    @PostMapping("/variants/{variantId}/bom/items")
    @PreAuthorize("hasAuthority('menu:bom:create')")
    public ResponseEntity<ApiResponse<ProductRecipeItemResponse>> addItem(
            @PathVariable UUID variantId,
            @Valid @RequestBody AddBomItemRequest request
    ) {
        log.info("Add BOM item: variantId={}", variantId);
        ProductRecipeItemResponse created = bomService.addItem(variantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(created, "Thêm nguyên vật liệu vào công thức thành công"));
    }

    /**
     * 3. Cập nhật một dòng nguyên vật liệu trong công thức định lượng (BOM).
     */
    @PutMapping("/variants/{variantId}/bom/items/{itemId}")
    @PreAuthorize("hasAuthority('menu:bom:update')")
    public ResponseEntity<ApiResponse<ProductRecipeItemResponse>> updateItem(
            @PathVariable UUID variantId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateBomItemRequest request
    ) {
        log.info("Update BOM item id={}, variantId={}", itemId, variantId);
        ProductRecipeItemResponse updated = bomService.updateItem(variantId, itemId, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Cập nhật dòng định lượng thành công"));
    }

    /**
     * 4. Gỡ (xóa mềm) một dòng nguyên vật liệu khỏi công thức định lượng (BOM).
     */
    @DeleteMapping("/variants/{variantId}/bom/items/{itemId}")
    @PreAuthorize("hasAuthority('menu:bom:delete')")
    public ResponseEntity<ApiResponse<Void>> removeItem(
            @PathVariable UUID variantId,
            @PathVariable UUID itemId
    ) {
        log.info("Remove BOM item id={}, variantId={}", itemId, variantId);
        bomService.removeItem(variantId, itemId);
        return ResponseEntity.ok(ApiResponse.success(null, "Gỡ nguyên vật liệu khỏi công thức thành công"));
    }

    /**
     * 5. Cập nhật / đồng bộ toàn bộ công thức định lượng (BOM) của biến thể.
     */
    @PutMapping("/variants/{variantId}/bom")
    @PreAuthorize("hasAuthority('menu:bom:update')")
    public ResponseEntity<ApiResponse<BomResponse>> syncBom(
            @PathVariable UUID variantId,
            @Valid @RequestBody BulkSyncBomRequest request
    ) {
        log.info("Sync BOM: variantId={}", variantId);
        BomResponse response = bomService.syncBom(variantId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Đồng bộ công thức định lượng thành công"));
    }

    /**
     * 6. Lấy danh sách tổng quan các biến thể kèm số lượng NVL trong định lượng để hiển thị bảng chính.
     */
    @GetMapping("/bom/overview")
    @PreAuthorize("hasAuthority('menu:bom:view')")
    public ResponseEntity<ApiResponse<PageResponse<ProductBomOverviewResponse>>> getBomOverview(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String bomStatus
    ) {
        log.info("Get BOM overview: page={}, size={}, search={}, categoryId={}, bomStatus={}", page, size, search, categoryId, bomStatus);
        PageResponse<ProductBomOverviewResponse> response =
                bomService.getBomOverview(page, size, search, categoryId, bomStatus);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy danh sách tổng quan định lượng thành công"));
    }
}
