package com.erp.backend_service.controller;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.ShiftOperationService;
import com.erp.core.dto.request.store.CloseShiftRequest;
import com.erp.core.dto.request.store.ConfirmShiftReportRequest;
import com.erp.core.dto.request.store.OpenShiftRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.store.ClosingSummaryResponse;
import com.erp.core.dto.response.store.ShiftAssignmentResponse;
import com.erp.core.dto.response.store.ShiftReportResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Controller vận hành ca làm việc: Mở ca, xem doanh số tức thời, kết ca, kiểm két và duyệt bàn giao.
 */
@RestController
@RequestMapping("/api/v1/shift-operations")
public class ShiftOperationController {

    private final ShiftOperationService shiftOperationService;

    public ShiftOperationController(ShiftOperationService shiftOperationService) {
        this.shiftOperationService = shiftOperationService;
    }

    @GetMapping("/my-active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ShiftAssignmentResponse>> getMyActiveShift() {
        UUID currentUserId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED));
        return ResponseEntity.ok(ApiResponse.success(shiftOperationService.getMyActiveShift(currentUserId)));
    }

    @PostMapping("/{id}/open")
    @PreAuthorize("hasAuthority('store:shift_assignment:update')")
    public ResponseEntity<ApiResponse<ShiftAssignmentResponse>> openShift(
            @PathVariable UUID id,
            @Valid @RequestBody OpenShiftRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED));
        return ResponseEntity.ok(ApiResponse.success(shiftOperationService.openShift(id, request, currentUserId)));
    }

    @GetMapping("/{id}/closing-summary")
    @PreAuthorize("hasAuthority('store:shift_assignment:view')")
    public ResponseEntity<ApiResponse<ClosingSummaryResponse>> getClosingSummary(@PathVariable UUID id) {
        UUID currentUserId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED));
        return ResponseEntity.ok(ApiResponse.success(shiftOperationService.getClosingSummary(id, currentUserId)));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('store:shift_report:create')")
    public ResponseEntity<ApiResponse<ShiftReportResponse>> closeShift(
            @PathVariable UUID id,
            @Valid @RequestBody CloseShiftRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED));
        return ResponseEntity.ok(ApiResponse.success(shiftOperationService.closeShift(id, request, currentUserId)));
    }

    @GetMapping("/assignment/{assignmentId}/report")
    @PreAuthorize("hasAuthority('store:shift_report:view')")
    public ResponseEntity<ApiResponse<ShiftReportResponse>> getReportByAssignment(@PathVariable UUID assignmentId) {
        return ResponseEntity.ok(ApiResponse.success(shiftOperationService.getShiftReportByAssignmentId(assignmentId)));
    }

    @PutMapping("/reports/{id}/confirm")
    @PreAuthorize("hasAuthority('store:shift_report:confirm')")
    public ResponseEntity<ApiResponse<ShiftReportResponse>> confirmReport(
            @PathVariable UUID id,
            @RequestBody(required = false) ConfirmShiftReportRequest request) {
        UUID managerId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED));
        String note = request != null ? request.note() : null;
        return ResponseEntity.ok(ApiResponse.success(shiftOperationService.confirmShiftReport(id, managerId, note)));
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAuthority('store:shift_report:view')")
    public ResponseEntity<ApiResponse<PageResponse<ShiftReportResponse>>> searchReports(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(shiftOperationService.searchShiftReports(branchId, businessDate, pageable)));
    }
}
