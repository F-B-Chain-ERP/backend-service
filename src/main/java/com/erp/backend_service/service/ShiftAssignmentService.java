package com.erp.backend_service.service;

import com.erp.core.dto.request.store.BulkAssignShiftRequest;
import com.erp.core.dto.request.store.CreateShiftAssignmentRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.store.ShiftAssignmentResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service lập lịch và phân công ca làm việc cho nhân viên.
 */
public interface ShiftAssignmentService {

    ShiftAssignmentResponse assignShift(CreateShiftAssignmentRequest request);

    List<ShiftAssignmentResponse> bulkAssignShifts(BulkAssignShiftRequest request);

    ShiftAssignmentResponse getAssignmentById(UUID id);

    PageResponse<ShiftAssignmentResponse> searchAssignments(UUID branchId, LocalDate startDate, LocalDate endDate, UUID accountId, String status, Pageable pageable);

    void cancelAssignment(UUID id, String reason);
}
