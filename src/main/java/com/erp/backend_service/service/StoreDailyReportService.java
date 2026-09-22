package com.erp.backend_service.service;

import com.erp.core.dto.request.store.CreateDailyReportRequest;
import com.erp.core.dto.request.store.UpdateDailyReportRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.store.StoreDailyReportResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service quản lý tổng hợp và phê duyệt báo cáo ngày của chi nhánh cửa hàng.
 */
public interface StoreDailyReportService {

    StoreDailyReportResponse generateDailyReport(CreateDailyReportRequest request, UUID currentUserId);

    StoreDailyReportResponse updateDailyReport(UUID id, UpdateDailyReportRequest request);

    StoreDailyReportResponse approveDailyReport(UUID id, UUID approverId, String note);

    StoreDailyReportResponse getDailyReportById(UUID id);

    StoreDailyReportResponse getDailyReportByDate(UUID branchId, LocalDate businessDate);

    PageResponse<StoreDailyReportResponse> searchDailyReports(UUID branchId, LocalDate startDate, LocalDate endDate, String status, Pageable pageable);
}
