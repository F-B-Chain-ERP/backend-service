package com.erp.backend_service.controller;

import com.erp.backend_service.service.WarehouseService;
import com.erp.core.dto.request.inv.CreateWarehouseRequest;
import com.erp.core.dto.request.inv.UpdateWarehouseRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.inv.WarehouseResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inv/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('inv:warehouse:view')")
    public ResponseEntity<ApiResponse<PageResponse<WarehouseResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String warehouseType,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(
                warehouseService.list(page, size, search, branchId, warehouseType, status)));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('inv:warehouse:view')")
    public ResponseEntity<ApiResponse<List<WarehouseResponse>>> listAll(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.listAll(status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('inv:warehouse:view')")
    public ResponseEntity<ApiResponse<WarehouseResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('inv:warehouse:create')")
    public ResponseEntity<ApiResponse<WarehouseResponse>> create(@Valid @RequestBody CreateWarehouseRequest request) {
        return ResponseEntity.created(null).body(ApiResponse.created(warehouseService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('inv:warehouse:update')")
    public ResponseEntity<ApiResponse<WarehouseResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateWarehouseRequest request) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('inv:warehouse:update')")
    public ResponseEntity<ApiResponse<WarehouseResponse>> updateStatus(
            @PathVariable UUID id, @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(ApiResponse.success(warehouseService.updateStatus(id, request.get("status"))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('inv:warehouse:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        warehouseService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
