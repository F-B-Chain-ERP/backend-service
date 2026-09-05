package com.erp.backend_service.controller;

import com.erp.backend_service.service.MaterialService;
import com.erp.core.dto.request.inv.CreateMaterialRequest;
import com.erp.core.dto.request.inv.UpdateMaterialRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.Material.MaterialResponse;
import com.erp.core.dto.response.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inv/materials")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('inv:material:view')")
    public ResponseEntity<ApiResponse<PageResponse<MaterialResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(materialService.list(page, size, search, categoryId, status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('inv:material:view')")
    public ResponseEntity<ApiResponse<MaterialResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(materialService.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('inv:material:create')")
    public ResponseEntity<ApiResponse<MaterialResponse>> create(@Valid @RequestBody CreateMaterialRequest request) {
        return ResponseEntity.created(null).body(ApiResponse.created(materialService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('inv:material:update')")
    public ResponseEntity<ApiResponse<MaterialResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateMaterialRequest request) {
        return ResponseEntity.ok(ApiResponse.success(materialService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('inv:material:update')")
    public ResponseEntity<ApiResponse<MaterialResponse>> updateStatus(
            @PathVariable UUID id, @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(ApiResponse.success(materialService.updateStatus(id, request.get("status"))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('inv:material:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        materialService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
