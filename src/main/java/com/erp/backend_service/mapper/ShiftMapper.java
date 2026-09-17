package com.erp.backend_service.mapper;

import com.erp.core.domain.Shift;
import com.erp.core.dto.response.store.ShiftResponse;
import org.springframework.stereotype.Component;

/**
 * Ánh xạ thực thể Shift sang ShiftResponse DTO.
 */
@Component
public class ShiftMapper {

    public ShiftResponse toResponse(Shift shift) {
        if (shift == null) {
            return null;
        }
        return new ShiftResponse(
                shift.getId(),
                shift.getBranchId(),
                shift.getShiftCode(),
                shift.getShiftName(),
                shift.getStartTime(),
                shift.getEndTime(),
                shift.getStatus(),
                shift.getCreatedAt(),
                shift.getUpdatedAt()
        );
    }
}
