package com.erp.backend_service.service;

import com.erp.core.dto.request.report.store.ExportDailyReportRequest;
import com.erp.core.dto.request.report.store.ExportShiftReportRequest;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * Service trích xuất và kết xuất báo cáo nghiệp vụ cửa hàng (Store & Shift).
 */
public interface StoreReportService {

    /**
     * Xuất báo cáo ngày kinh doanh (Store Daily Report).
     */
    ResponseEntity<?> exportDailyReport(ExportDailyReportRequest request, UUID currentUserId);

    /**
     * Xuất báo cáo ca làm việc (Shift Report).
     */
    ResponseEntity<?> exportShiftReport(ExportShiftReportRequest request, UUID currentUserId);
}
