package com.erp.backend_service.service;

import com.erp.core.dto.request.report.fin.ExportFinancialReportRequest;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * Service trích xuất và kết xuất báo cáo tài chính và chi phí (Finance).
 */
public interface FinReportService {

    /**
     * Xuất báo cáo tổng hợp tài chính chi nhánh (Excel / PDF).
     */
    ResponseEntity<?> exportFinancialReport(ExportFinancialReportRequest request, UUID currentUserId);
}
