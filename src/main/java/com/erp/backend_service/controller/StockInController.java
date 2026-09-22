package com.erp.backend_service.controller;

import com.erp.backend_service.service.StockInService;
import com.erp.core.dto.request.inv.CreateStockInRequest;
import com.erp.core.dto.request.inv.StatusUpdateRequest;
import com.erp.core.dto.request.inv.UpdateStockInRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.inv.StockInResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Quản lý phiếu nhập kho: truy vấn phân trang, chi tiết, tạo (kèm dòng chi tiết),
 * cập nhật (chỉ DRAFT) và ghi sổ/hủy qua {@code PATCH /{id}/status} (chỉ DRAFT).
 */
@RestController
@RequestMapping("/api/v1/inv/stock-ins")
public class StockInController {

    private final StockInService stockInService;

    private static final Logger log = LoggerFactory.getLogger(StockInController.class);

    public StockInController(StockInService stockInService) {
        this.stockInService = stockInService;
    }

    /** Danh sách phiếu nhập kho phân trang (lọc theo mã, trạng thái, kho, nguồn nhập, khoảng ngày). */
    @GetMapping
    @PreAuthorize("hasAuthority('inv:stock_in:view')")
    public ResponseEntity<ApiResponse<PageResponse<StockInResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        log.info("Get list stock-ins: keyword={}, page={}, size={}", search, page, size);
        return ResponseEntity.ok(ApiResponse.success(
                stockInService.list(page, size, search, status, warehouseId, sourceType, fromDate, toDate),
                "Lấy danh sách phiếu nhập kho thành công"));
    }

    /** Chi tiết một phiếu nhập kho (kèm dòng chi tiết). */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('inv:stock_in:view')")
    public ResponseEntity<ApiResponse<StockInResponse>> get(@PathVariable UUID id) {
        log.info("Get stock-in {}", id);
        return ResponseEntity.ok(ApiResponse.success(stockInService.get(id), "Lấy thông tin phiếu nhập kho thành công"));
    }

    /** Tạo mới phiếu nhập kho ở trạng thái DRAFT (mã phiếu do hệ thống sinh). */
    @PostMapping
    @PreAuthorize("hasAuthority('inv:stock_in:create')")
    public ResponseEntity<ApiResponse<StockInResponse>> create(@Valid @RequestBody CreateStockInRequest request) {
        log.info("Create stock-in: warehouseId={}, sourceType={}", request.warehouseId(), request.sourceType());
        return ResponseEntity.ok(ApiResponse.success(stockInService.create(request), "Tạo phiếu nhập kho thành công"));
    }

    /** Cập nhật phiếu nhập kho (chỉ DRAFT). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('inv:stock_in:update')")
    public ResponseEntity<ApiResponse<StockInResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateStockInRequest request) {
        log.info("Update stock-in id={}", id);
        return ResponseEntity.ok(ApiResponse.success(stockInService.update(id, request), "Cập nhật phiếu nhập kho thành công"));
    }

    /** Ghi sổ (POSTED) hoặc hủy (CANCELLED) phiếu nhập kho — chỉ khi DRAFT. */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('inv:stock_in:update')")
    public ResponseEntity<ApiResponse<StockInResponse>> changeStatus(
            @PathVariable UUID id, @Valid @RequestBody StatusUpdateRequest request) {
        log.info("Change stock-in status id={}, status={}", id, request.status());
        return ResponseEntity.ok(ApiResponse.success(stockInService.changeStatus(id, request), "Cập nhật trạng thái phiếu nhập kho thành công"));
    }
}