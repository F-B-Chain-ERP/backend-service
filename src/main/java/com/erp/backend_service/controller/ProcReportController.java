package com.erp.backend_service.controller;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.ProcReportService;
import com.erp.core.dto.request.report.proc.ExportPurchaseOrderReportRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller tiếp nhận yêu cầu xuất báo cáo phân hệ mua hàng (Đơn mua hàng PO).
 */
@RestController
@RequestMapping("/api/v1/reports/proc")
public class ProcReportController {

    private final ProcReportService procReportService;

    public ProcReportController(ProcReportService procReportService) {
        this.procReportService = procReportService;
    }

    /**
     * Xuất danh sách đơn mua hàng PO (Excel / PDF).
     */
    @PostMapping("/purchase-orders/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> exportPurchaseOrders(@Valid @RequestBody ExportPurchaseOrderReportRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED));
        return procReportService.exportPurchaseOrderReport(request, currentUserId);
    }
}
