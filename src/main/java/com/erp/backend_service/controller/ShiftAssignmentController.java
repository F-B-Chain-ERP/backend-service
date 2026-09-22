package com.erp.backend_service.controller;

import com.erp.backend_service.service.ShiftAssignmentService;
import com.erp.core.dto.request.store.BulkAssignShiftRequest;
import com.erp.core.dto.request.store.CreateShiftAssignmentRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.store.ShiftAssignmentResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller quản lý lịch phân công ca làm việc của nhân viên.
 */
@RestController
@RequestMapping("/api/v1/shift-assignments")
public class ShiftAssignmentController {

    private final ShiftAssignmentService shiftAssignmentService;
    private static final Logger log = LoggerFactory.getLogger(ShiftAssignmentController.class);

    public ShiftAssignmentController(ShiftAssignmentService shiftAssignmentService) {
        this.shiftAssignmentService = shiftAssignmentService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('store:shift_assignment:create')")
    public ResponseEntity<ApiResponse<ShiftAssignmentResponse>> assign(
            @Valid @RequestBody CreateShiftAssignmentRequest request) {
        log.info("Assign shift: branchId={}, shiftId={}, accountId={}, workDate={}", request.branchId(), request.shiftId(), request.accountId(), request.workDate());
        return ResponseEntity.ok(ApiResponse.created(shiftAssignmentService.assignShift(request)));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAuthority('store:shift_assignment:create')")
    public ResponseEntity<ApiResponse<List<ShiftAssignmentResponse>>> bulkAssign(
            @Valid @RequestBody BulkAssignShiftRequest request) {
        log.info("Bulk assign shifts: branchId={}, count={}", request.branchId(), request.assignments().size());
        return ResponseEntity.ok(ApiResponse.created(shiftAssignmentService.bulkAssignShifts(request)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('store:shift_assignment:view')")
    public ResponseEntity<ApiResponse<ShiftAssignmentResponse>> get(@PathVariable UUID id) {
        log.info("Get {}", id);
        return ResponseEntity.ok(ApiResponse.success(shiftAssignmentService.getAssignmentById(id)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('store:shift_assignment:view')")
    public ResponseEntity<ApiResponse<PageResponse<ShiftAssignmentResponse>>> search(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("Search assignments: branchId={}, startDate={}, endDate={}, accountId={}, status={}", branchId, startDate, endDate, accountId, status);
        return ResponseEntity.ok(ApiResponse.success(
                shiftAssignmentService.searchAssignments(branchId, startDate, endDate, accountId, status, pageable)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('store:shift_assignment:delete')")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        log.info("Cancel shift assignment id={}", id);
        shiftAssignmentService.cancelAssignment(id, reason);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã hủy ca phân công thành công"));
    }
}
