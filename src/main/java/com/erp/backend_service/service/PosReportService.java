package com.erp.backend_service.service;

import com.erp.core.dto.request.report.pos.ExportOrderReportRequest;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * Service trích xuất và kết xuất báo cáo bán hàng phân hệ POS.
 */
public interface PosReportService {

    /**
     * Xuất danh sách đơn hàng POS theo khoảng ngày và bộ lọc (Sync nếu <= 500, Async qua RabbitMQ nếu > 500).
     */
    ResponseEntity<?> exportOrderReport(ExportOrderReportRequest request, UUID currentUserId);
}
