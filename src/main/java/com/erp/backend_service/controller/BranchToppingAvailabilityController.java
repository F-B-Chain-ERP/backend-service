package com.erp.backend_service.controller;

import com.erp.backend_service.service.BranchToppingAvailabilityService;
import com.erp.core.dto.request.menu.UpdateBranchToppingRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.BranchToppingAvailabilityResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/menu/branches")
@Validated
public class BranchToppingAvailabilityController {

    private final BranchToppingAvailabilityService service;

    public BranchToppingAvailabilityController(BranchToppingAvailabilityService service) {
        this.service = service;
    }

    @GetMapping("/{branchId}/toppings")
    @PreAuthorize("hasAuthority('menu:topping_availability:view')")
    public ResponseEntity<ApiResponse<PageResponse<BranchToppingAvailabilityResponse>>> list(
            @PathVariable UUID branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(
                service.list(branchId, page, size, search, status)));
    }

    @PutMapping("/{branchId}/toppings/{toppingId}")
    @PreAuthorize("hasAuthority('menu:topping_availability:update')")
    public ResponseEntity<ApiResponse<BranchToppingAvailabilityResponse>> updateAvailability(
            @PathVariable UUID branchId,
            @PathVariable UUID toppingId,
            @Valid @RequestBody UpdateBranchToppingRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                service.updateAvailability(branchId, toppingId, request)));
    }
}
