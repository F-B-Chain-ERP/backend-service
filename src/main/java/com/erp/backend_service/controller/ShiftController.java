package com.erp.backend_service.controller;

import com.erp.backend_service.service.ShiftService;
import com.erp.core.dto.request.store.CreateShiftRequest;
import com.erp.core.dto.request.store.UpdateShiftRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.store.ShiftResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller quản lý danh mục khung ca làm việc chuẩn (Shift Template).
 */
@RestController
@RequestMapping("/api/v1/shifts")
public class ShiftController {

    private final ShiftService shiftService;
    private static final Logger log = LoggerFactory.getLogger(ShiftController.class);

    public ShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('store:shift:create')")
    public ResponseEntity<ApiResponse<ShiftResponse>> create(@Valid @RequestBody CreateShiftRequest request) {
        log.info("Create shift: branchId={}, shiftCode={}", request.branchId(), request.shiftCode());
        return ResponseEntity.ok(ApiResponse.created(shiftService.createShift(request)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('store:shift:view')")
    public ResponseEntity<ApiResponse<ShiftResponse>> get(@PathVariable UUID id) {
        log.info("Get {}", id);
        return ResponseEntity.ok(ApiResponse.success(shiftService.getShiftById(id)));
    }

    @GetMapping("/branch/{branchId}")
    @PreAuthorize("hasAuthority('store:shift:view')")
    public ResponseEntity<ApiResponse<List<ShiftResponse>>> getByBranch(
            @PathVariable UUID branchId,
            @RequestParam(required = false) String status) {
        log.info("Get shifts by branch: branchId={}, status={}", branchId, status);
        return ResponseEntity.ok(ApiResponse.success(shiftService.getShiftsByBranch(branchId, status)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('store:shift:view')")
    public ResponseEntity<ApiResponse<PageResponse<ShiftResponse>>> search(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("Search shifts: branchId={}, status={}, page={}, size={}", branchId, status, pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(ApiResponse.success(shiftService.searchShifts(branchId, status, pageable)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('store:shift:update')")
    public ResponseEntity<ApiResponse<ShiftResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateShiftRequest request) {
        log.info("Update id={}", id);
        return ResponseEntity.ok(ApiResponse.success(shiftService.updateShift(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('store:shift:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        log.info("Delete id={}", id);
        shiftService.deleteShift(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa ca làm việc thành công"));
    }
}
