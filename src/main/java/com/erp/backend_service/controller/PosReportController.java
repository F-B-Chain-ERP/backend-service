package com.erp.backend_service.controller;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.PosReportService;
import com.erp.core.dto.request.report.pos.ExportOrderReportRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Controller tiếp nhận yêu cầu xuất báo cáo đơn hàng POS.
 */
@RestController
@RequestMapping("/api/v1/reports/pos")
public class PosReportController {

    private final PosReportService posReportService;
    private static final Logger log = LoggerFactory.getLogger(PosReportController.class);

    public PosReportController(PosReportService posReportService) {
        this.posReportService = posReportService;
    }

    /**
     * Xuất danh sách đơn hàng POS (Excel / PDF).
     */
    @PostMapping("/orders/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> exportOrders(@Valid @RequestBody ExportOrderReportRequest request) {
        log.info("Create report export: module=POS, type={}, format={}", request.reportType(), request.format());
        UUID currentUserId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED));
        return posReportService.exportOrderReport(request, currentUserId);
    }
}
