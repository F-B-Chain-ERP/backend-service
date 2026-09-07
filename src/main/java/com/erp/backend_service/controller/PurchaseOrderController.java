package com.erp.backend_service.controller;

import com.erp.backend_service.service.PurchaseOrderService;
import com.erp.core.dto.request.proc.CreatePurchaseOrderRequest;
import com.erp.core.dto.request.proc.ReceivePurchaseOrderRequest;
import com.erp.core.dto.request.proc.UpdatePurchaseOrderRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.proc.PurchaseOrderResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Quản lý đơn mua hàng: truy vấn phân trang, tạo (kèm dòng chi tiết), cập nhật, xóa
 * và các chuyển trạng thái (submit/approve/cancel), ghi nhận thực nhận từ Kho.
 */
@RestController
@RequestMapping("/api/v1/proc/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    /** Danh sách đơn mua hàng phân trang (lọc theo mã, trạng thái, NCC, kho, khoảng ngày). */
    @GetMapping
    @PreAuthorize("hasAuthority('proc:purchase_order:view')")
    public ResponseEntity<ApiResponse<PageResponse<PurchaseOrderResponse>>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(ApiResponse.success(
                purchaseOrderService.list(page, size, search, status, supplierId, warehouseId, fromDate, toDate)));
    }

    /** Chi tiết một đơn mua hàng (kèm dòng chi tiết). */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('proc:purchase_order:view')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(purchaseOrderService.get(id)));
    }

    /** Tạo mới đơn mua hàng (trạng thái DRAFT). */
    @PostMapping
    @PreAuthorize("hasAuthority('proc:purchase_order:create')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> create(
            @Valid @RequestBody CreatePurchaseOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success(purchaseOrderService.create(request)));
    }

    /** Cập nhật đơn mua hàng (chỉ DRAFT). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('proc:purchase_order:update')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdatePurchaseOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success(purchaseOrderService.update(id, request)));
    }

    /** Xóa đơn mua hàng (chỉ DRAFT). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('proc:purchase_order:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        purchaseOrderService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /** Chuyển DRAFT -> SUBMITTED. */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('proc:purchase_order:update')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(purchaseOrderService.submit(id)));
    }

    /** Duyệt đơn mua hàng (SUBMITTED -> APPROVED). */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('proc:purchase_order:update')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(purchaseOrderService.approve(id)));
    }

    /** Hủy đơn mua hàng. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('proc:purchase_order:update')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> cancel(
            @PathVariable UUID id, @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.success(purchaseOrderService.cancel(id, reason)));
    }

    /** Từ chối đơn mua hàng (SUBMITTED → DRAFT). */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('proc:purchase_order:update')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> reject(
            @PathVariable UUID id, @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.success(purchaseOrderService.reject(id, reason)));
    }

    /** Ghi nhận số lượng thực nhận từ phân hệ Kho (INV), cập nhật trạng thái nhận hàng. */
    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('proc:purchase_order:update')")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> receive(
            @PathVariable UUID id, @Valid @RequestBody ReceivePurchaseOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success(purchaseOrderService.receive(id, request)));
    }
}
