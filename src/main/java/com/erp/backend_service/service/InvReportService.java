package com.erp.backend_service.service;

import com.erp.core.dto.request.report.inv.ExportStockReportRequest;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * Service trích xuất và kết xuất báo cáo kho hàng (Inventory).
 */
public interface InvReportService {

    /**
     * Xuất báo cáo số dư tồn kho nguyên vật liệu theo kho (Excel / PDF).
     */
    ResponseEntity<?> exportStockReport(ExportStockReportRequest request, UUID currentUserId);
}
