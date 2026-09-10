package com.erp.backend_service.controller;

import com.erp.backend_service.service.BomService;
import com.erp.core.dto.request.menu.CreateBomItemRequest;
import com.erp.core.dto.request.menu.UpdateBomItemRequest;
import com.erp.core.dto.request.menu.UpdateBomRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.menu.BomItemResponse;
import com.erp.core.dto.response.menu.BomResponse;
import com.erp.core.dto.response.menu.UpdateBomResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Quản lý BOM (định mức nguyên vật liệu pha chế) theo_variant.
 * <p>Validate toàn bộ nghiệp vụ ở tầng Service, Controller chỉ nhận request và trả response.</p>
 */
@RestController
@RequestMapping("/api/v1/menu")
public class BomController {

    private final BomService bomService;

    public BomController(BomService bomService) {
        this.bomService = bomService;
    }

    /**
     * Lấy toàn bộ BOM của_variant.
     * Nếu variant chưa có BOM → trả items rỗng.
     */
    @GetMapping("/variants/{id}/bom")
    @PreAuthorize("hasAuthority('menu:bom:view')")
    public ResponseEntity<ApiResponse<BomResponse>> getBom(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(bomService.getBom(id)));
    }

    /**
     * Thêm một dòng BOM mới vào_variant.
     */
    @PostMapping("/variants/{id}/bom/items")
    @PreAuthorize("hasAuthority('menu:bom:create')")
    public ResponseEntity<ApiResponse<BomItemResponse>> createItem(
            @PathVariable UUID id,
            @RequestBody CreateBomItemRequest request) {
        return ResponseEntity.created(null)
                .body(ApiResponse.created(bomService.createItem(id, request)));
    }

    /**
     * Cập nhật quantity và wastagePercent của một dòng BOM.
     */
    @PutMapping("/bom/items/{id}")
    @PreAuthorize("hasAuthority('menu:bom:update')")
    public ResponseEntity<ApiResponse<BomItemResponse>> updateItem(
            @PathVariable UUID id,
            @RequestBody UpdateBomItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success(bomService.updateItem(id, request)));
    }

    /**
     * Xóa硬(delete) một dòng BOM.
     */
    @DeleteMapping("/bom/items/{id}")
    @PreAuthorize("hasAuthority('menu:bom:delete')")
    public ResponseEntity<ApiResponse<Void>> deleteItem(@PathVariable UUID id) {
        bomService.deleteItem(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Gỡ nguyên vật liệu khỏi BOM thành công"));
    }

    /**
     * Thay thế toàn bộ BOM của_variant (bulk replace).
     * Danh sách items rỗng ({@code []}) hợp lệ — xóa toàn bộ BOM.
     */
    @PutMapping("/variants/{id}/bom")
    @PreAuthorize("hasAuthority('menu:bom:update')")
    public ResponseEntity<ApiResponse<UpdateBomResponse>> bulkReplace(
            @PathVariable UUID id,
            @RequestBody UpdateBomRequest request) {
        return ResponseEntity.ok(ApiResponse.success(bomService.bulkReplace(id, request)));
    }
}
