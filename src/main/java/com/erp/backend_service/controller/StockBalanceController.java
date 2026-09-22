package com.erp.backend_service.controller;

import com.erp.backend_service.service.StockBalanceService;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.inv.StockBalanceResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inv/stocks")
public class StockBalanceController {

    private final StockBalanceService stockBalanceService;

    private static final Logger log = LoggerFactory.getLogger(StockBalanceController.class);

    public StockBalanceController(StockBalanceService stockBalanceService) {
        this.stockBalanceService = stockBalanceService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('inv:stock_balance:view')")
    public ResponseEntity<ApiResponse<PageResponse<StockBalanceResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID materialId,
            @RequestParam(required = false) String search
    ) {
        log.info("Get list stock balances: page={}, size={}, warehouseId={}, materialId={}", page, size, warehouseId, materialId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        stockBalanceService.list(
                                page,
                                size,
                                warehouseId,
                                materialId,
                                search
                        )
                )
        );
    }

    @GetMapping("/warehouse/{warehouseId}/material/{materialId}")
    @PreAuthorize("hasAuthority('inv:stock_balance:view')")
    public ResponseEntity<ApiResponse<StockBalanceResponse>> get(
            @PathVariable UUID warehouseId,
            @PathVariable UUID materialId
    ) {
        log.info("Get stock balance: warehouseId={}, materialId={}", warehouseId, materialId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        stockBalanceService.get(warehouseId, materialId)
                )
        );
    }
}