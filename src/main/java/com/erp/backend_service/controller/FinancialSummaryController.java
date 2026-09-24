package com.erp.backend_service.controller;

import com.erp.backend_service.service.FinancialSummaryService;
import com.erp.core.dto.request.fin.RecalculateFinancialSummaryRequest;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.fin.FinancialSummaryResponse;
import com.erp.core.dto.response.fin.FinancialSummarySourceResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * REST Controller báo cáo tài chính ngày chi nhánh (S5-04).
 * Cung cấp API xem tổng quan/chi tiết, đối soát dữ liệu nguồn, tính lại và chốt kỳ.
 */
@RestController
@RequestMapping("/api/v1/fin/financial-summaries")
public class FinancialSummaryController {

    private final FinancialSummaryService financialSummaryService;

    public FinancialSummaryController(FinancialSummaryService financialSummaryService) {
        this.financialSummaryService = financialSummaryService;
    }

    /** Danh sách báo cáo tài chính theo chi nhánh/khoảng ngày/trạng thái (phân trang). */
    @GetMapping
    @PreAuthorize("hasAuthority('fin:financial_summary:view')")
    public ResponseEntity<ApiResponse<PageResponse<FinancialSummaryResponse>>> list(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "businessDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(ApiResponse.success(
                financialSummaryService.list(branchId, fromDate, toDate, status, page, size, sortBy, sortDir)));
    }

    /** Chi tiết một báo cáo tài chính ngày. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('fin:financial_summary:view')")
    public ResponseEntity<ApiResponse<FinancialSummaryResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(financialSummaryService.get(id)));
    }

    /** Xem dữ liệu nguồn (ORDERS | REFUNDS | EXPENSES) để đối soát một báo cáo. */
    @GetMapping("/{id}/sources")
    @PreAuthorize("hasAuthority('fin:financial_summary:view')")
    public ResponseEntity<ApiResponse<FinancialSummarySourceResponse<?>>> getSources(
            @PathVariable UUID id,
            @RequestParam String source,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.success(
                financialSummaryService.getSources(id, source, page, size)));
    }

    /** Tính lại toàn bộ chỉ tiêu theo chi nhánh + ngày kinh doanh (kết quả về trạng thái DRAFT). */
    @PostMapping("/recalculate")
    @PreAuthorize("hasAuthority('fin:financial_summary:update')")
    public ResponseEntity<ApiResponse<FinancialSummaryResponse>> recalculate(
            @Valid @RequestBody RecalculateFinancialSummaryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(financialSummaryService.recalculate(request)));
    }

    /** Chốt kỳ báo cáo từ DRAFT sang FINALIZED. */
    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasAuthority('fin:financial_summary:update')")
    public ResponseEntity<ApiResponse<FinancialSummaryResponse>> finalize(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(financialSummaryService.finalize(id)));
    }
}