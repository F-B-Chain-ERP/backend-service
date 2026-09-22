package com.erp.backend_service.controller;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.StoreReportService;
import com.erp.core.dto.request.report.store.ExportDailyReportRequest;
import com.erp.core.dto.request.report.store.ExportShiftReportRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller tiếp nhận yêu cầu xuất báo cáo phân hệ cửa hàng (Báo cáo ngày & Báo cáo ca).
 */
@RestController
@RequestMapping("/api/v1/reports/store")
public class StoreReportController {

    private final StoreReportService storeReportService;
    private static final Logger log = LoggerFactory.getLogger(StoreReportController.class);

    public StoreReportController(StoreReportService storeReportService) {
        this.storeReportService = storeReportService;
    }

    /**
     * Xuất báo cáo doanh thu ngày của chi nhánh (Excel / PDF).
     */
    @PostMapping("/daily/export")
    @PreAuthorize("hasAuthority('store:daily_report:view')")
    public ResponseEntity<?> exportDailyReport(@Valid @RequestBody ExportDailyReportRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED));
        log.info("Export daily report: branchId={}, from={}, to={}, format={}, userId={}", request.branchId(), request.startDate(), request.endDate(), request.format(), currentUserId);
        return storeReportService.exportDailyReport(request, currentUserId);
    }

    /**
     * Xuất báo cáo biên bản chốt ca làm việc (Excel / PDF).
     */
    @PostMapping("/shift/export")
    @PreAuthorize("hasAuthority('store:shift:view')")
    public ResponseEntity<?> exportShiftReport(@Valid @RequestBody ExportShiftReportRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED));
        log.info("Export shift report: branchId={}, businessDate={}, format={}, userId={}", request.branchId(), request.businessDate(), request.format(), currentUserId);
        return storeReportService.exportShiftReport(request, currentUserId);
    }
}
