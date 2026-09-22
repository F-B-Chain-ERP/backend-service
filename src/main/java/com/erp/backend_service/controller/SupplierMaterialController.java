package com.erp.backend_service.controller;

import com.erp.backend_service.service.SupplierMaterialService;
import com.erp.core.dto.request.proc.SupplierMaterial.CreateSupplierMaterialRequest;
import com.erp.core.dto.request.proc.SupplierMaterial.UpdateSupplierMaterialRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.SupplierMaterial.SupplierMaterialResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/proc")
public class SupplierMaterialController {

    private final SupplierMaterialService supplierMaterialService;
    private static final Logger log = LoggerFactory.getLogger(SupplierMaterialController.class);

    public SupplierMaterialController(SupplierMaterialService supplierMaterialService) {
        this.supplierMaterialService = supplierMaterialService;
    }

    @GetMapping("/suppliers/{supplierId}/materials")
    @PreAuthorize("hasAuthority('proc:supplier_material:view')")
    public ResponseEntity<ApiResponse<PageResponse<SupplierMaterialResponse>>> listBySupplier(
            @PathVariable UUID supplierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        log.info("Get list by supplier: supplierId={}, keyword={}, page={}, size={}", supplierId, search, page, size);
        return ResponseEntity.ok(ApiResponse.success(
                supplierMaterialService.list(page, size, supplierId, null, search)));
    }

    @PostMapping("/suppliers/{supplierId}/materials")
    @PreAuthorize("hasAuthority('proc:supplier_material:create')")
    public ResponseEntity<ApiResponse<SupplierMaterialResponse>> create(
            @PathVariable UUID supplierId,
            @Valid @RequestBody CreateSupplierMaterialRequest request) {
        log.info("Create supplier material: supplierId={}, materialId={}", request.supplierId(), request.materialId());
        return ResponseEntity.ok(ApiResponse.success(supplierMaterialService.create(request)));
    }

    @PutMapping("/supplier-materials/{id}")
    @PreAuthorize("hasAuthority('proc:supplier_material:update')")
    public ResponseEntity<ApiResponse<SupplierMaterialResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateSupplierMaterialRequest request) {
        log.info("Update id={}", id);
        return ResponseEntity.ok(ApiResponse.success(supplierMaterialService.update(id, request)));
    }

    @DeleteMapping("/supplier-materials/{id}")
    @PreAuthorize("hasAuthority('proc:supplier_material:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        log.info("Delete id={}", id);
        supplierMaterialService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}