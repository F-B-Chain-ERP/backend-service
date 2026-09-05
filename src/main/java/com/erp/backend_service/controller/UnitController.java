package com.erp.backend_service.controller;

import com.erp.backend_service.service.UnitService;
import com.erp.core.dto.request.menu.CreateUnitRequest;
import com.erp.core.dto.request.menu.UpdateUnitRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.UnitResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Quản lý đơn vị tính (master dùng chung INV + MENU).
 */
@RestController
@RequestMapping("/api/v1/menu/units")
public class UnitController {

    private final UnitService unitService;

    public UnitController(UnitService unitService) {
        this.unitService = unitService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('menu:unit:view')")
    public ResponseEntity<ApiResponse<PageResponse<UnitResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String unitType,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(unitService.list(page, size, search, unitType, status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:unit:view')")
    public ResponseEntity<ApiResponse<UnitResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(unitService.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('menu:unit:create')")
    public ResponseEntity<ApiResponse<UnitResponse>> create(@Valid @RequestBody CreateUnitRequest request) {
        return ResponseEntity.created(null).body(ApiResponse.created(unitService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:unit:update')")
    public ResponseEntity<ApiResponse<UnitResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateUnitRequest request) {
        return ResponseEntity.ok(ApiResponse.success(unitService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('menu:unit:update')")
    public ResponseEntity<ApiResponse<UnitResponse>> updateStatus(
            @PathVariable UUID id, @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(ApiResponse.success(unitService.updateStatus(id, request.get("status"))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:unit:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        unitService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
