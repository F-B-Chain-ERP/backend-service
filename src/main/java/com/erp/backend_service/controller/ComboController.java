package com.erp.backend_service.controller;

import com.erp.backend_service.service.ComboService;
import com.erp.backend_service.service.ProductService;
import com.erp.core.dto.request.menu.AddComboItemRequest;
import com.erp.core.dto.request.menu.BulkSyncComboItemsRequest;
import com.erp.core.dto.request.menu.CalculateComboPriceRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.CalculateComboPriceResponse;
import com.erp.core.dto.response.menu.ComboDetailResponse;
import com.erp.core.dto.response.menu.ProductResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Quản lý Combo: xem danh sách, chi tiết, thêm/xóa thành phần (per-item),
 * đồng bộ (bulk sync), và tính giá linh hoạt khi khách hàng đổi biến thể.
 * Combo là sản phẩm có isCombo=true, thành phần là các biến thể sản phẩm khác.
 */
@RestController
@RequestMapping("/api/v1/menu/combos")
@Validated
public class ComboController {

    private final ComboService comboService;
    private final ProductService productService;

    public ComboController(ComboService comboService, ProductService productService) {
        this.comboService = comboService;
        this.productService = productService;
    }

    /** Lấy danh sách Combo (products có isCombo=true) với phân trang. */
    @GetMapping
    @PreAuthorize("hasAuthority('menu:combo:view')")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        PageResponse<ProductResponse> response = productService.list(
                page, size, search, categoryId, status, null, null, true, sortBy, sortDirection
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** Lấy chi tiết Combo kèm danh sách thành phần ACTIVE. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:combo:view')")
    public ResponseEntity<ApiResponse<ComboDetailResponse>> getDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(comboService.getDetail(id)));
    }

    /** Thêm một thành phần vào Combo (per-item). */
    @PostMapping("/{id}/items")
    @PreAuthorize("hasAuthority('menu:combo:update')")
    public ResponseEntity<ApiResponse<ComboDetailResponse>> addItem(
            @PathVariable UUID id,
            @Valid @RequestBody AddComboItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success(comboService.addItem(id, request)));
    }

    /** Xóa mềm một thành phần khỏi Combo (per-item). */
    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize("hasAuthority('menu:combo:update')")
    public ResponseEntity<ApiResponse<ComboDetailResponse>> removeItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId) {
        return ResponseEntity.ok(ApiResponse.success(comboService.removeItem(id, itemId)));
    }

    /** Đồng bộ toàn bộ thành phần Combo (thêm mới, cập nhật, xóa mềm). Atomic transaction. */
    @PutMapping("/{id}/items")
    @PreAuthorize("hasAuthority('menu:combo:update')")
    public ResponseEntity<ApiResponse<ComboDetailResponse>> syncItems(
            @PathVariable UUID id,
            @Valid @RequestBody BulkSyncComboItemsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(comboService.syncItems(id, request)));
    }

    /** Tính giá combo linh hoạt khi khách hàng đổi biến thể (flex pricing). */
    @PostMapping("/calculate-price")
    @PreAuthorize("hasAuthority('menu:combo:view')")
    public ResponseEntity<ApiResponse<CalculateComboPriceResponse>> calculatePrice(
            @Valid @RequestBody CalculateComboPriceRequest request) {
        return ResponseEntity.ok(ApiResponse.success(comboService.calculatePrice(request)));
    }
}
