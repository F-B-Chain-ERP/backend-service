package com.erp.backend_service.controller;

import com.erp.backend_service.service.PickupTimeSlotService;
import com.erp.core.dto.request.branch.CreatePickupTimeSlotRequest;
import com.erp.core.dto.request.branch.GeneratePickupSlotsRequest;
import com.erp.core.dto.request.branch.UpdatePickupTimeSlotRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.branch.PickupTimeSlotResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Controller quản lý các khung giờ nhận hàng tại quán (Pickup Time Slots).
 */
@RestController
public class PickupSlotController {

    private final PickupTimeSlotService pickupTimeSlotService;

    public PickupSlotController(PickupTimeSlotService pickupTimeSlotService) {
        this.pickupTimeSlotService = pickupTimeSlotService;
    }

    /**
     * Lấy danh sách khung giờ pickup của chi nhánh (nội bộ quản trị).
     */
    @GetMapping("/api/v1/branches/{branchId}/pickup-slots")
    @PreAuthorize("hasAuthority('sys:pickup_slot:view')")
    public ResponseEntity<ApiResponse<List<PickupTimeSlotResponse>>> listSlots(
            @PathVariable UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(pickupTimeSlotService.getSlots(branchId, date, true)));
    }

    /**
     * Lấy danh sách khung giờ pickup khả dụng của chi nhánh (Public - Khách hàng Storefront đặt món).
     */
    @GetMapping("/api/v1/public/branches/{branchId}/pickup-slots")
    public ResponseEntity<ApiResponse<List<PickupTimeSlotResponse>>> listPublicSlots(
            @PathVariable UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(pickupTimeSlotService.getSlots(branchId, date, false)));
    }

    /**
     * Tạo mới một khung giờ pickup.
     */
    @PostMapping("/api/v1/branches/{branchId}/pickup-slots")
    @PreAuthorize("hasAuthority('sys:pickup_slot:create')")
    public ResponseEntity<ApiResponse<PickupTimeSlotResponse>> createSlot(
            @PathVariable UUID branchId,
            @Valid @RequestBody CreatePickupTimeSlotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(pickupTimeSlotService.createSlot(branchId, request)));
    }

    /**
     * Cập nhật một khung giờ pickup.
     */
    @PutMapping("/api/v1/branches/{branchId}/pickup-slots/{id}")
    @PreAuthorize("hasAuthority('sys:pickup_slot:update')")
    public ResponseEntity<ApiResponse<PickupTimeSlotResponse>> updateSlot(
            @PathVariable UUID branchId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePickupTimeSlotRequest request) {
        return ResponseEntity.ok(ApiResponse.success(pickupTimeSlotService.updateSlot(branchId, id, request)));
    }

    /**
     * Xóa một khung giờ pickup (nếu đã có đơn thì chuyển INACTIVE).
     */
    @DeleteMapping("/api/v1/branches/{branchId}/pickup-slots/{id}")
    @PreAuthorize("hasAuthority('sys:pickup_slot:delete')")
    public ResponseEntity<ApiResponse<Void>> deleteSlot(
            @PathVariable UUID branchId,
            @PathVariable UUID id) {
        pickupTimeSlotService.deleteSlot(branchId, id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Tự động sinh danh sách các khung giờ pickup theo bước nhảy thời gian.
     */
    @PostMapping("/api/v1/branches/{branchId}/pickup-slots/generate")
    @PreAuthorize("hasAuthority('sys:pickup_slot:create')")
    public ResponseEntity<ApiResponse<List<PickupTimeSlotResponse>>> generateSlots(
            @PathVariable UUID branchId,
            @Valid @RequestBody GeneratePickupSlotsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(pickupTimeSlotService.generateSlots(branchId, request)));
    }
}
