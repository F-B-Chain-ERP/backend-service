package com.erp.backend_service.controller;

import com.erp.backend_service.service.StockOutService;
import com.erp.core.dto.request.inv.CreateStockOutRequest;
import com.erp.core.dto.request.inv.StatusUpdateRequest;
import com.erp.core.dto.request.inv.UpdateStockOutRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.inv.StockOutResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Quản lý phiếu xuất kho: truy vấn phân trang, chi tiết, tạo (kèm dòng chi tiết),
 * cập nhật (chỉ DRAFT) và ghi sổ/hủy qua {@code PATCH /{id}/status} (chỉ DRAFT).
 */
@RestController
@RequestMapping("/api/v1/inv/stock-outs")
public class StockOutController {

    private final StockOutService stockOutService;

    public StockOutController(StockOutService stockOutService) {
        this.stockOutService = stockOutService;
    }

    /** Danh sách phiếu xuất kho phân trang (lọc theo mã, trạng thái, kho, loại đích xuất, khoảng ngày). */
    @GetMapping
    @PreAuthorize("hasAuthority('inv:stock_out:view')")
    public ResponseEntity<ApiResponse<PageResponse<StockOutResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String destinationType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(ApiResponse.success(
                stockOutService.list(page, size, search, status, warehouseId, destinationType, fromDate, toDate),
                "Lấy danh sách phiếu xuất kho thành công"));
    }

    /** Chi tiết một phiếu xuất kho (kèm dòng chi tiết). */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('inv:stock_out:view')")
    public ResponseEntity<ApiResponse<StockOutResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(stockOutService.get(id), "Lấy thông tin phiếu xuất kho thành công"));
    }

    /** Tạo mới phiếu xuất kho ở trạng thái DRAFT (mã phiếu do hệ thống sinh). */
    @PostMapping
    @PreAuthorize("hasAuthority('inv:stock_out:create')")
    public ResponseEntity<ApiResponse<StockOutResponse>> create(@Valid @RequestBody CreateStockOutRequest request) {
        return ResponseEntity.ok(ApiResponse.success(stockOutService.create(request), "Tạo phiếu xuất kho thành công"));
    }

    /** Cập nhật phiếu xuất kho (chỉ DRAFT). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('inv:stock_out:update')")
    public ResponseEntity<ApiResponse<StockOutResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateStockOutRequest request) {
        return ResponseEntity.ok(ApiResponse.success(stockOutService.update(id, request), "Cập nhật phiếu xuất kho thành công"));
    }

    /** Ghi sổ (POSTED) hoặc hủy (CANCELLED) phiếu xuất kho — chỉ khi DRAFT. */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('inv:stock_out:update')")
    public ResponseEntity<ApiResponse<StockOutResponse>> changeStatus(
            @PathVariable UUID id, @Valid @RequestBody StatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(stockOutService.changeStatus(id, request), "Cập nhật trạng thái phiếu xuất kho thành công"));
    }
}