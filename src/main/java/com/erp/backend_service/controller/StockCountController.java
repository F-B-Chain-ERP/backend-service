package com.erp.backend_service.controller;

import com.erp.backend_service.service.StockCountService;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.request.inv.CreateStockCountRequest;
import com.erp.core.dto.request.inv.UpdateStockCountRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.inv.StockCountResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inv/stock-counts")
public class StockCountController {

    private final StockCountService stockCountService;

    public StockCountController(
            StockCountService stockCountService
    ) {
        this.stockCountService = stockCountService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('inv:stock_count:view')")
    public ResponseEntity<ApiResponse<PageResponse<StockCountResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID warehouseId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        stockCountService.list(
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
    @PreAuthorize("hasAuthority('inv:stock_count:view')")
    public ResponseEntity<ApiResponse<StockCountResponse>> get(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        stockCountService.get(id)
                )
        );
    }

    @PostMapping
    @PreAuthorize("hasAuthority('inv:stock_count:create')")
    public ResponseEntity<ApiResponse<StockCountResponse>> create(
            @Valid @RequestBody CreateStockCountRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        stockCountService.create(request)
                )
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('inv:stock_count:update')")
    public ResponseEntity<ApiResponse<StockCountResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStockCountRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        stockCountService.update(
                                id,
                                request
                        )
                )
        );
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('inv:stock_count:update')")
    public ResponseEntity<ApiResponse<StockCountResponse>> start(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(stockCountService.start(id))
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('inv:stock_count:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id
    ) {
        stockCountService.delete(id);

        return ResponseEntity.ok(
                ApiResponse.success(null)
        );
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('inv:stock_count:update')")
    public ResponseEntity<ApiResponse<StockCountResponse>> complete(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        stockCountService.complete(id)
                )
        );
    }

    @PostMapping("/{id}/adjust")
    @PreAuthorize("hasAuthority('inv:stock_count:update')")
    public ResponseEntity<ApiResponse<StockCountResponse>> adjust(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        stockCountService.adjust(id)
                )
        );
    }
}
