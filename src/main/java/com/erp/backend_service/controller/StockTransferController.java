package com.erp.backend_service.controller;

import com.erp.backend_service.service.StockTransferService;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.request.inv.CreateStockTransferRequest;
import com.erp.core.dto.request.inv.ReceiveStockTransferRequest;
import com.erp.core.dto.request.inv.UpdateStockTransferRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.inv.StockTransferResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inv/transfers")
public class StockTransferController {

    private final StockTransferService stockTransferService;

    public StockTransferController(
            StockTransferService stockTransferService
    ) {
        this.stockTransferService = stockTransferService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('inv:stock_transfer:view')")
    public ResponseEntity<ApiResponse<PageResponse<StockTransferResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID warehouseId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        stockTransferService.list(
                                page,
                                size,
                                search,
                                status,
                                warehouseId
                        )
                )
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('inv:stock_transfer:view')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> get(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        stockTransferService.get(id)
                )
        );
    }

    @PostMapping
    @PreAuthorize("hasAuthority('inv:stock_transfer:create')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> create(
            @Valid @RequestBody CreateStockTransferRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        stockTransferService.create(request)
                )
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('inv:stock_transfer:update')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStockTransferRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        stockTransferService.update(
                                id,
                                request
                        )
                )
        );
    }

    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasAuthority('inv:stock_transfer:update')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> dispatch(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        stockTransferService.dispatch(id)
                )
        );
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('inv:stock_transfer:update')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> receive(
            @PathVariable UUID id,
            @Valid @RequestBody ReceiveStockTransferRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        stockTransferService.receive(
                                id,
                                request
                        )
                )
        );
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('inv:stock_transfer:update')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> cancel(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        stockTransferService.cancel(id)
                )
        );
    }
}
