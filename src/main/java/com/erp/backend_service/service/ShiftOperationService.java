package com.erp.backend_service.service;

import com.erp.core.dto.request.store.CloseShiftRequest;
import com.erp.core.dto.request.store.OpenShiftRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.store.ClosingSummaryResponse;
import com.erp.core.dto.response.store.ShiftAssignmentResponse;
import com.erp.core.dto.response.store.ShiftReportResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service vận hành mở ca, đóng ca, kiểm kê két và phê duyệt bàn giao ca.
 */
public interface ShiftOperationService {

    ShiftAssignmentResponse getMyActiveShift(UUID accountId);

    ShiftAssignmentResponse openShift(UUID assignmentId, OpenShiftRequest request, UUID currentUserId);

    ClosingSummaryResponse getClosingSummary(UUID assignmentId, UUID currentUserId);

    ShiftReportResponse closeShift(UUID assignmentId, CloseShiftRequest request, UUID currentUserId);

    ShiftReportResponse confirmShiftReport(UUID reportId, UUID managerId, String note);

    ShiftReportResponse getShiftReportByAssignmentId(UUID assignmentId);

    PageResponse<ShiftReportResponse> searchShiftReports(UUID branchId, LocalDate businessDate, Pageable pageable);
}
