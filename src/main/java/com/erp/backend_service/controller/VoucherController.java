package com.erp.backend_service.controller;

import com.erp.backend_service.service.VoucherService;
import com.erp.backend_service.service.VoucherUsageService;
import com.erp.core.dto.request.menu.CreateVoucherRequest;
import com.erp.core.dto.request.menu.UpdateVoucherRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.VoucherDetailResponse;
import com.erp.core.dto.response.menu.VoucherResponse;
import com.erp.core.dto.response.menu.VoucherUsageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Quản lý voucher: truy vấn phân trang, chi tiết, tạo, cập nhật, đổi trạng thái,
 * xóa mềm và lịch sử sử dụng.
 */
@RestController
@RequestMapping("/api/v1/menu/vouchers")
public class VoucherController {

    private final VoucherService voucherService;
    private final VoucherUsageService voucherUsageService;

    public VoucherController(VoucherService voucherService, VoucherUsageService voucherUsageService) {
        this.voucherService = voucherService;
        this.voucherUsageService = voucherUsageService;
    }

    /** Danh sách voucher phân trang, lọc theo status/discountType/thời gian hiệu lực/chi nhánh. */
    @GetMapping
    @PreAuthorize("hasAuthority('menu:voucher:view')")
    public ResponseEntity<ApiResponse<PageResponse<VoucherResponse>>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String discountType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTo,
            @RequestParam(required = false) UUID branchId) {
        return ResponseEntity.ok(ApiResponse.success(voucherService.list(
                page, size, search, status, discountType, startFrom, startTo, endFrom, endTo, branchId)));
    }

    /** Chi tiết voucher kèm danh sách chi nhánh được gán. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:voucher:view')")
    public ResponseEntity<ApiResponse<VoucherDetailResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(voucherService.get(id)));
    }

    /** Tạo mới voucher. */
    @PostMapping
    @PreAuthorize("hasAuthority('menu:voucher:create')")
    public ResponseEntity<ApiResponse<VoucherResponse>> create(@Valid @RequestBody CreateVoucherRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(voucherService.create(request)));
    }

    /** Cập nhật voucher. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:voucher:update')")
    public ResponseEntity<ApiResponse<VoucherResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateVoucherRequest request) {
        return ResponseEntity.ok(ApiResponse.success(voucherService.update(id, request)));
    }

    /** Khóa / mở khóa voucher. */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('menu:voucher:update')")
    public ResponseEntity<ApiResponse<VoucherResponse>> updateStatus(
            @PathVariable UUID id, @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(ApiResponse.success(voucherService.updateStatus(id, request.get("status"))));
    }

    /** Xóa mềm voucher (chuyển sang INACTIVE, giữ lịch sử sử dụng). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:voucher:delete')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        voucherService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Lịch sử sử dụng voucher, phân trang. */
    @GetMapping("/{id}/usage")
    @PreAuthorize("hasAuthority('menu:voucher_usage:view')")
    public ResponseEntity<ApiResponse<PageResponse<VoucherUsageResponse>>> usage(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.success(voucherUsageService.listByVoucher(page, size, id)));
    }
}