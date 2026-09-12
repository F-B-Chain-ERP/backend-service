package com.erp.backend_service.controller;

import com.erp.backend_service.service.VoucherBranchService;
import com.erp.core.dto.request.menu.AssignVoucherBranchRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.menu.VoucherBranchResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Gán voucher cho chi nhánh: xem danh sách, thêm, gỡ bỏ.
 */
@RestController
@RequestMapping("/api/v1/menu/vouchers/{voucherId}/branches")
public class VoucherBranchController {

    private final VoucherBranchService voucherBranchService;

    public VoucherBranchController(VoucherBranchService voucherBranchService) {
        this.voucherBranchService = voucherBranchService;
    }

    /** Danh sách chi nhánh được gán cho voucher. */
    @GetMapping
    @PreAuthorize("hasAuthority('menu:voucher_branch:view')")
    public ResponseEntity<ApiResponse<List<VoucherBranchResponse>>> list(@PathVariable UUID voucherId) {
        return ResponseEntity.ok(ApiResponse.success(voucherBranchService.getBranches(voucherId)));
    }

    /** Gán voucher cho danh sách chi nhánh. */
    @PostMapping
    @PreAuthorize("hasAuthority('menu:voucher_branch:create')")
    public ResponseEntity<ApiResponse<List<VoucherBranchResponse>>> assign(
            @PathVariable UUID voucherId, @Valid @RequestBody AssignVoucherBranchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(voucherBranchService.assign(voucherId, request)));
    }

    /** Gỡ voucher khỏi một chi nhánh. */
    @DeleteMapping("/{branchId}")
    @PreAuthorize("hasAuthority('menu:voucher_branch:delete')")
    public ResponseEntity<Void> remove(@PathVariable UUID voucherId, @PathVariable UUID branchId) {
        voucherBranchService.remove(voucherId, branchId);
        return ResponseEntity.noContent().build();
    }
}