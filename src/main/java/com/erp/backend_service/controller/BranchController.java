package com.erp.backend_service.controller;

import com.erp.backend_service.service.BranchService;
import com.erp.core.dto.request.branch.CreateBranchRequest;
import com.erp.core.dto.request.branch.UpdateBranchRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.branch.BranchResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Quản lý chi nhánh: truy vấn (gồm cây chi nhánh cha-con), tạo, cập nhật, xóa.
 * Các thao tác ghi chỉ dành cho tài khoản có quyền quản trị (FULL_PERMISSION).
 * Danh sách chi nhánh theo quyền (mine) dùng cho bước chọn đơn vị sau đăng nhập.
 */
@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    /** Danh sách tất cả chi nhánh (kèm tên chi nhánh cha). */
    @GetMapping
    @PreAuthorize("hasAuthority('sys:branch:view')")
    public ResponseEntity<ApiResponse<List<BranchResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(branchService.findAll()));
    }

    /** Danh sách chi nhánh thuộc phạm vi (scope) của tài khoản đang đăng nhập. */
    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('sys:branch:view')")
    public ResponseEntity<ApiResponse<List<BranchResponse>>> mine() {
        return ResponseEntity.ok(ApiResponse.success(branchService.findMine()));
    }

    /** Chi tiết một chi nhánh. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:branch:view')")
    public ResponseEntity<ApiResponse<BranchResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(branchService.findById(id)));
    }

    /** Tạo mới chi nhánh (quản trị). */
    @PostMapping
    @PreAuthorize("hasAuthority('sys:branch:create')")
    public ResponseEntity<ApiResponse<BranchResponse>> create(@Valid @RequestBody CreateBranchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(branchService.create(request)));
    }

    /** Cập nhật chi nhánh (quản trị). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:branch:update')")
    public ResponseEntity<ApiResponse<BranchResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateBranchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(branchService.update(id, request)));
    }

    /** Xóa chi nhánh (quản trị). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:branch:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        branchService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
