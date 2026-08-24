package com.erp.backend_service.controller;

import com.erp.backend_service.service.SupplierService;
import com.erp.core.dto.request.proc.CreateSupplierRequest;
import com.erp.core.dto.request.proc.UpdateSupplierRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.SupplierResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Quản lý nhà cung cấp: truy vấn phân trang, tạo, cập nhật, xóa.
 */
@RestController
@RequestMapping("/api/v1/proc/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    /** Danh sách nhà cung cấp phân trang. */
    @GetMapping
    @PreAuthorize("hasAuthority('proc:supplier:view')")
    public ResponseEntity<ApiResponse<PageResponse<SupplierResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(supplierService.list(page, size, search, status)));
    }

    /** Chi tiết một nhà cung cấp. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('proc:supplier:view')")
    public ResponseEntity<ApiResponse<SupplierResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(supplierService.get(id)));
    }

    /** Tạo mới nhà cung cấp. */
    @PostMapping
    @PreAuthorize("hasAuthority('proc:supplier:create')")
    public ResponseEntity<ApiResponse<SupplierResponse>> create(@Valid @RequestBody CreateSupplierRequest request) {
        return ResponseEntity.ok(ApiResponse.success(supplierService.create(request)));
    }

    /** Cập nhật nhà cung cấp. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('proc:supplier:update')")
    public ResponseEntity<ApiResponse<SupplierResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateSupplierRequest request) {
        return ResponseEntity.ok(ApiResponse.success(supplierService.update(id, request)));
    }

    /** Xóa nhà cung cấp. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('proc:supplier:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        supplierService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
