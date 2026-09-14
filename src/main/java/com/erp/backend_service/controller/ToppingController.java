package com.erp.backend_service.controller;

import com.erp.backend_service.service.ToppingService;
import com.erp.core.dto.request.menu.CreateToppingRequest;
import com.erp.core.dto.request.menu.UpdateToppingRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.ToppingResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Quản lý topping: truy vấn phân trang, tạo, cập nhật, xóa (hard delete).
 */
@RestController
@RequestMapping("/api/v1/menu/toppings")
@Validated
public class ToppingController {

    private final ToppingService toppingService;

    public ToppingController(ToppingService toppingService) {
        this.toppingService = toppingService;
    }

    /** Danh sách topping phân trang (tìm kiếm theo mã/tên, lọc theo nhóm/trạng thái). */
    @GetMapping
    @PreAuthorize("hasAuthority('menu:topping:view')")
    public ResponseEntity<ApiResponse<PageResponse<ToppingResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String groupName,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(
                toppingService.list(page, size, search, groupName, status)));
    }

    /** Chi tiết một topping. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:topping:view')")
    public ResponseEntity<ApiResponse<ToppingResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(toppingService.get(id)));
    }

    /** Tạo mới topping. */
    @PostMapping
    @PreAuthorize("hasAuthority('menu:topping:create')")
    public ResponseEntity<ApiResponse<ToppingResponse>> create(
            @Valid @RequestBody CreateToppingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(toppingService.create(request), "Tạo topping thành công"));
    }

    /** Cập nhật topping (bao gồm trạng thái ACTIVE/INACTIVE). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:topping:update')")
    public ResponseEntity<ApiResponse<ToppingResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateToppingRequest request) {
        return ResponseEntity.ok(ApiResponse.success(toppingService.update(id, request)));
    }

    /** Xóa topping (hard delete, yêu cầu chưa được gán cho sản phẩm). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:topping:delete')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        toppingService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
