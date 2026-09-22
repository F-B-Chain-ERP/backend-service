package com.erp.backend_service.controller;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.FinReportService;
import com.erp.backend_service.service.InvReportService;
import com.erp.backend_service.service.PosReportService;
import com.erp.backend_service.service.ProcReportService;
import com.erp.backend_service.service.StoreReportService;
import com.erp.core.constants.ReportExportConstants;
import com.erp.core.dto.request.report.ExportReportRequest;
import com.erp.core.dto.request.report.fin.ExportFinancialReportRequest;
import com.erp.core.dto.request.report.inv.ExportStockReportRequest;
import com.erp.core.dto.request.report.pos.ExportOrderReportRequest;
import com.erp.core.dto.request.report.proc.ExportPurchaseOrderReportRequest;
import com.erp.core.dto.request.report.store.ExportDailyReportRequest;
import com.erp.core.dto.request.report.store.ExportShiftReportRequest;
import com.erp.backend_service.security.ReportPermissionRegistry;
import com.erp.core.enums.ReportType;
import com.erp.core.enums.ReportModule;
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
 * Endpoint thống nhất xuất báo cáo cho mọi phân hệ.
 *
 * <p>Nhận {@link ExportReportRequest} (module, reportType, format, mode AUTO/SYNC/ASYNC,
 * phạm vi từngày/đến ngày, chi nhánh) và điều phối xuống service chuyên biệt theo phân hệ.
 * Phản hồi: file nhị phân (200) khi xử lý đồng bộ, hoặc {@code ApiResponse<ReportJobResponse>} (202)
 * khi xử lý bất đồng bộ.</p>
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportExportController {

    private final PosReportService posReportService;
    private final StoreReportService storeReportService;
    private final FinReportService finReportService;
    private final InvReportService invReportService;
    private final ProcReportService procReportService;
    private final ReportPermissionRegistry reportPermissionRegistry;
    private static final Logger log = LoggerFactory.getLogger(ReportExportController.class);

    public ReportExportController(PosReportService posReportService,
                                  StoreReportService storeReportService,
                                  FinReportService finReportService,
                                  InvReportService invReportService,
                                  ProcReportService procReportService,
                                  ReportPermissionRegistry reportPermissionRegistry) {
        this.posReportService = posReportService;
        this.storeReportService = storeReportService;
        this.finReportService = finReportService;
        this.invReportService = invReportService;
        this.procReportService = procReportService;
        this.reportPermissionRegistry = reportPermissionRegistry;
    }

    /**
     * Xuất báo cáo theo phân hệ và loại báo cáo (Excel/PDF).
     */
    @PostMapping("/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> export(@Valid @RequestBody ExportReportRequest request) {
        log.info("Create report export: module={}, type={}, format={}", request.module(), request.reportType(), request.format());
        UUID currentUserId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED));

        if (request.module() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Phân hệ báo cáo không được để trống");
        }

        // Kiểm tra quyền hạn chuyên biệt theo loại báo cáo
        ReportType reportType = ReportType.from(request.reportType());
        reportPermissionRegistry.checkPermission(reportType);

        return switch (request.module()) {
            case POS -> exportPos(request, currentUserId);
            case STORE -> exportStore(request, currentUserId);
            case FIN -> finReportService.exportFinancialReport(
                    new ExportFinancialReportRequest(
                            request.branchId(),
                            null,
                            request.fromDate(),
                            request.toDate(),
                            request.format(),
                            request.mode()),
                    currentUserId);
            case INV -> invReportService.exportStockReport(
                    new ExportStockReportRequest(
                            null,
                            null,
                            null,
                            request.fromDate(),
                            request.toDate(),
                            request.format(),
                            request.mode()),
                    currentUserId);
            case PROC -> procReportService.exportPurchaseOrderReport(
                    new ExportPurchaseOrderReportRequest(
                            null,
                            null,
                            null,
                            null,
                            request.fromDate(),
                            request.toDate(),
                            request.format(),
                            request.mode()),
                    currentUserId);
            case SYSTEM -> throw new BaseException(ErrorCode.REPORT_400_UNSUPPORTED_TYPE, "Phân hệ SYSTEM chưa có báo cáo.");
        };
    }

    private ResponseEntity<?> exportPos(ExportReportRequest request, UUID currentUserId) {
        ReportType resolvedType = ReportType.from(request.reportType());
        if (resolvedType == null) {
            resolvedType = ReportType.POS_ORDER_LIST;
        }
        if (resolvedType != ReportType.POS_ORDER_LIST && resolvedType != ReportType.POS_SALES_SUMMARY) {
            throw new BaseException(ErrorCode.REPORT_400_UNSUPPORTED_TYPE,
                    "Loại báo cáo POS không được hỗ trợ: " + request.reportType());
        }
        return posReportService.exportOrderReport(
                new ExportOrderReportRequest(
                        request.branchId(),
                        null,
                        null,
                        request.fromDate(),
                        request.toDate(),
                        request.format(),
                        request.mode(),
                        resolvedType.getCode()),
                currentUserId);
    }

    private ResponseEntity<?> exportStore(ExportReportRequest request, UUID currentUserId) {
        ReportType resolvedType = ReportType.from(request.reportType());
        if (resolvedType == ReportType.STORE_DAILY_CLOSING || resolvedType == ReportType.STORE_DAILY_LIST) {
            return storeReportService.exportDailyReport(
                    new ExportDailyReportRequest(
                            request.branchId(),
                            request.fromDate(),
                            request.toDate(),
                            null,
                            request.format(),
                            request.mode()),
                    currentUserId);
        }
        if (resolvedType == ReportType.STORE_SHIFT_HANDOVER || resolvedType == ReportType.STORE_SHIFT_LIST) {
            return storeReportService.exportShiftReport(
                    new ExportShiftReportRequest(
                            request.branchId(),
                            null,
                            request.fromDate(),
                            request.toDate(),
                            null,
                            request.format(),
                            request.mode()),
                    currentUserId);
        }
        throw new BaseException(ErrorCode.REPORT_400_UNSUPPORTED_TYPE,
                "Loại báo cáo STORE không được hỗ trợ: " + request.reportType());
    }
}