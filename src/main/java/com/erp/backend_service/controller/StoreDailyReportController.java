package com.erp.backend_service.controller;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.StoreDailyReportService;
import com.erp.core.dto.request.store.CreateDailyReportRequest;
import com.erp.core.dto.request.store.UpdateDailyReportRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.store.StoreDailyReportResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Controller quản lý tổng hợp và khóa sổ báo cáo ngày cửa hàng.
 */
@RestController
@RequestMapping("/api/v1/store-daily-reports")
public class StoreDailyReportController {

    private final StoreDailyReportService storeDailyReportService;

    public StoreDailyReportController(StoreDailyReportService storeDailyReportService) {
        this.storeDailyReportService = storeDailyReportService;
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('store:daily_report:create')")
    public ResponseEntity<ApiResponse<StoreDailyReportResponse>> generate(
            @Valid @RequestBody CreateDailyReportRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED));
        return ResponseEntity.ok(ApiResponse.created(storeDailyReportService.generateDailyReport(request, currentUserId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('store:daily_report:view')")
    public ResponseEntity<ApiResponse<StoreDailyReportResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(storeDailyReportService.getDailyReportById(id)));
    }

    @GetMapping("/by-date")
    @PreAuthorize("hasAuthority('store:daily_report:view')")
    public ResponseEntity<ApiResponse<StoreDailyReportResponse>> getByDate(
            @RequestParam UUID branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return ResponseEntity.ok(ApiResponse.success(storeDailyReportService.getDailyReportByDate(branchId, businessDate)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('store:daily_report:view')")
    public ResponseEntity<ApiResponse<PageResponse<StoreDailyReportResponse>>> search(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                storeDailyReportService.searchDailyReports(branchId, startDate, endDate, status, pageable)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('store:daily_report:update')")
    public ResponseEntity<ApiResponse<StoreDailyReportResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDailyReportRequest request) {
        return ResponseEntity.ok(ApiResponse.success(storeDailyReportService.updateDailyReport(id, request)));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('store:daily_report:approve')")
    public ResponseEntity<ApiResponse<StoreDailyReportResponse>> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        UUID approverId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED));
        String note = body != null ? body.get("note") : null;
        return ResponseEntity.ok(ApiResponse.success(storeDailyReportService.approveDailyReport(id, approverId, note)));
    }
}
