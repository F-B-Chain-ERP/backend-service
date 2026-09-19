package com.erp.backend_service.service;

import com.erp.core.dto.request.report.proc.ExportPurchaseOrderReportRequest;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * Service trích xuất và kết xuất báo cáo mua hàng (Procurement).
 */
public interface ProcReportService {

    /**
     * Xuất danh sách đơn mua hàng (Purchase Order) theo bộ lọc.
     */
    ResponseEntity<?> exportPurchaseOrderReport(ExportPurchaseOrderReportRequest request, UUID currentUserId);
}
