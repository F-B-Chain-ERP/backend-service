package com.erp.backend_service.controller;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.FinReportService;
import com.erp.core.dto.request.report.fin.ExportFinancialReportRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller tiếp nhận yêu cầu xuất báo cáo phân hệ tài chính (Tổng hợp tài chính chi nhánh).
 */
@RestController
@RequestMapping("/api/v1/reports/fin")
public class FinReportController {

    private final FinReportService finReportService;

    public FinReportController(FinReportService finReportService) {
        this.finReportService = finReportService;
    }

    /**
     * Xuất báo cáo tổng hợp tài chính chi nhánh (Excel / PDF).
     */
    @PostMapping("/financial/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> exportFinancialReport(@Valid @RequestBody ExportFinancialReportRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED));
        return finReportService.exportFinancialReport(request, currentUserId);
    }
}
