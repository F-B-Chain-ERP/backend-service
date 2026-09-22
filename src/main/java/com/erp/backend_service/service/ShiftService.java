package com.erp.backend_service.service;

import com.erp.core.dto.request.store.CreateShiftRequest;
import com.erp.core.dto.request.store.UpdateShiftRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.store.ShiftResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Service quản lý danh mục khung ca làm việc chuẩn (Shift Templates).
 */
public interface ShiftService {

    ShiftResponse createShift(CreateShiftRequest request);

    ShiftResponse updateShift(UUID id, UpdateShiftRequest request);

    ShiftResponse getShiftById(UUID id);

    List<ShiftResponse> getShiftsByBranch(UUID branchId, String status);

    PageResponse<ShiftResponse> searchShifts(UUID branchId, String status, String query, Pageable pageable);

    void deleteShift(UUID id);
}
