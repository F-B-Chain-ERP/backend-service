package com.erp.backend_service.controller;

import com.erp.backend_service.service.SupplierMaterialService;
import com.erp.core.dto.request.proc.SupplierMaterial.CreateSupplierMaterialRequest;
import com.erp.core.dto.request.proc.SupplierMaterial.UpdateSupplierMaterialRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.SupplierMaterial.SupplierMaterialResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/proc/supplier-materials")
public class SupplierMaterialController {

    private final SupplierMaterialService supplierMaterialService;

    public SupplierMaterialController(SupplierMaterialService supplierMaterialService) {
        this.supplierMaterialService = supplierMaterialService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('proc:supplier_material:view')")
    public ResponseEntity<ApiResponse<PageResponse<SupplierMaterialResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) UUID materialId,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiResponse.success(
                supplierMaterialService.list(page, size, supplierId, materialId, search)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('proc:supplier_material:view')")
    public ResponseEntity<ApiResponse<SupplierMaterialResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(supplierMaterialService.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('proc:supplier_material:create')")
    public ResponseEntity<ApiResponse<SupplierMaterialResponse>> create(
            @Valid @RequestBody CreateSupplierMaterialRequest request) {
        return ResponseEntity.ok(ApiResponse.success(supplierMaterialService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('proc:supplier_material:update')")
    public ResponseEntity<ApiResponse<SupplierMaterialResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateSupplierMaterialRequest request) {
        return ResponseEntity.ok(ApiResponse.success(supplierMaterialService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('proc:supplier_material:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        supplierMaterialService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
