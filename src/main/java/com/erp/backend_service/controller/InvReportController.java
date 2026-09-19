package com.erp.backend_service.controller;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.InvReportService;
import com.erp.core.dto.request.report.inv.ExportStockReportRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller tiếp nhận yêu cầu xuất báo cáo phân hệ kho hàng (Tồn kho).
 */
@RestController
@RequestMapping("/api/v1/reports/inv")
public class InvReportController {

    private final InvReportService invReportService;

    public InvReportController(InvReportService invReportService) {
        this.invReportService = invReportService;
    }

    /**
     * Xuất báo cáo số dư tồn kho nguyên vật liệu (Excel / PDF).
     */
    @PostMapping("/stock/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> exportStockReport(@Valid @RequestBody ExportStockReportRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED));
        return invReportService.exportStockReport(request, currentUserId);
    }
}
