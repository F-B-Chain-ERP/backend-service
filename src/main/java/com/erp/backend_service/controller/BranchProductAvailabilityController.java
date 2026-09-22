package com.erp.backend_service.controller;

import com.erp.backend_service.service.BranchProductAvailabilityService;
import com.erp.core.dto.request.menu.UpdateBranchProductRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.BranchProductAvailabilityResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/menu/branches")
@Validated
public class BranchProductAvailabilityController {

    private final BranchProductAvailabilityService service;
    private static final Logger log = LoggerFactory.getLogger(BranchProductAvailabilityController.class);

    public BranchProductAvailabilityController(BranchProductAvailabilityService service) {
        this.service = service;
    }

    @GetMapping("/{branchId}/products")
    @PreAuthorize("hasAuthority('menu:product_availability:view')")
    public ResponseEntity<ApiResponse<PageResponse<BranchProductAvailabilityResponse>>> list(
            @PathVariable UUID branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID categoryId) {
        log.info("Get list: branchId={}, keyword={}, page={}, size={}, status={}, categoryId={}", branchId, search, page, size, status, categoryId);
        return ResponseEntity.ok(ApiResponse.success(
                service.list(branchId, page, size, search, status, categoryId)));
    }

    @PutMapping("/{branchId}/products/{productId}")
    @PreAuthorize("hasAuthority('menu:product_availability:update')")
    public ResponseEntity<ApiResponse<BranchProductAvailabilityResponse>> updateAvailability(
            @PathVariable UUID branchId,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateBranchProductRequest request) {
        log.info("Update product availability: branchId={}, productId={}", branchId, productId);
        return ResponseEntity.ok(ApiResponse.success(
                service.updateAvailability(branchId, productId, request)));
    }
}
