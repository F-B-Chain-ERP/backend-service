package com.erp.backend_service.controller;

import com.erp.backend_service.service.ComboService;
import com.erp.core.dto.request.menu.BulkSyncComboItemsRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.menu.ComboDetailResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Quản lý Combo: xem chi tiết và đồng bộ thành phần (bulk sync).
 * Combo là sản phẩm có isCombo=true, thành phần là các biến thể sản phẩm khác.
 */
@RestController
@RequestMapping("/api/v1/menu/combos")
@Validated
public class ComboController {

    private final ComboService comboService;

    public ComboController(ComboService comboService) {
        this.comboService = comboService;
    }

    /** Lấy chi tiết Combo kèm danh sách thành phần ACTIVE. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:combo:view')")
    public ResponseEntity<ApiResponse<ComboDetailResponse>> getDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(comboService.getDetail(id)));
    }

    /** Đồng bộ toàn bộ thành phần Combo (thêm mới, cập nhật, xóa mềm). Atomic transaction. */
    @PutMapping("/{id}/items")
    @PreAuthorize("hasAuthority('menu:combo:update')")
    public ResponseEntity<ApiResponse<ComboDetailResponse>> syncItems(
            @PathVariable UUID id,
            @Valid @RequestBody BulkSyncComboItemsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(comboService.syncItems(id, request)));
    }
}
