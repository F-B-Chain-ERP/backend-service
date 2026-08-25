package com.erp.backend_service.controller;

import com.erp.backend_service.service.ScopeService;
import com.erp.core.dto.auth.ScopeAdminResponse;
import com.erp.core.dto.request.scope.CreateScopeRequest;
import com.erp.core.dto.request.scope.UpdateScopeRequest;
import com.erp.core.dto.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Quản lý phạm vi truy cập (Scope): truy vấn danh sách, chi tiết, tạo, cập nhật, xóa.
 * Phạm vi được dùng để giới hạn dữ liệu theo loại (ALL_SYSTEM/STORE/WAREHOUSE) và chi nhánh.
 */
@RestController
@RequestMapping("/api/v1/scopes")
public class ScopeController {

    private final ScopeService scopeService;

    public ScopeController(ScopeService scopeService) {
        this.scopeService = scopeService;
    }

    /** Danh sách toàn bộ phạm vi kèm tên chi nhánh liên quan. */
    @GetMapping
    @PreAuthorize("hasAuthority('sys:scope:view')")
    public ResponseEntity<ApiResponse<List<ScopeAdminResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(scopeService.findAll()));
    }

    /** Chi tiết một phạm vi. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:scope:view')")
    public ResponseEntity<ApiResponse<ScopeAdminResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(scopeService.getById(id)));
    }

    /** Tạo mới phạm vi. */
    @PostMapping
    @PreAuthorize("hasAuthority('sys:scope:create')")
    public ResponseEntity<ApiResponse<ScopeAdminResponse>> create(@Valid @RequestBody CreateScopeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(scopeService.create(request)));
    }

    /** Cập nhật phạm vi (cập nhật một phần, null = giữ nguyên). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:scope:update')")
    public ResponseEntity<ApiResponse<ScopeAdminResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateScopeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(scopeService.update(id, request)));
    }

    /** Xóa vĩnh viễn một phạm vi. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:scope:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        scopeService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
