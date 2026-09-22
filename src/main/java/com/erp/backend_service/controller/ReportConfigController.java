package com.erp.backend_service.controller;

import com.erp.backend_service.configuration.ReportProperties;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.core.constants.ReportExportConstants;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.report.ReportConfigResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller cung cấp cấu hình xuất báo cáo cho Frontend.
 *
 * <p>Endpoint {@code GET /api/v1/reports/config} trả về toàn bộ ngưỡng Sync/Async,
 * định dạng hỗ trợ, chu kỳ polling và trạng thái SSE — giúp Frontend không cần
 * hard-code trùng lặp cấu hình.</p>
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportConfigController {

    private final ReportProperties reportProperties;

    public ReportConfigController(ReportProperties reportProperties) {
        this.reportProperties = reportProperties;
    }

    @GetMapping("/config")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReportConfigResponse>> getConfig() {
        ReportConfigResponse config = new ReportConfigResponse(
                reportProperties.getAsyncThresholdRecords(),
                reportProperties.getMaxHardSyncRecords(),
                List.of("EXCEL", "PDF"),
                List.of("AUTO", "SYNC", "ASYNC"),
                reportProperties.getPollIntervalMs(),
                reportProperties.isSseEnabled()
        );
        return ResponseEntity.ok(ApiResponse.success(config));
    }
}
