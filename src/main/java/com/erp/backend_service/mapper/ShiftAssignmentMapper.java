package com.erp.backend_service.mapper;

import com.erp.core.domain.Account;
import com.erp.core.domain.Shift;
import com.erp.core.domain.ShiftAssignment;
import com.erp.core.dto.response.store.ShiftAssignmentResponse;
import org.springframework.stereotype.Component;

/**
 * Ánh xạ thực thể ShiftAssignment kèm thông tin Shift và Account sang DTO.
 */
@Component
public class ShiftAssignmentMapper {

    public ShiftAssignmentResponse toResponse(ShiftAssignment assignment, Shift shift, Account account) {
        if (assignment == null) {
            return null;
        }

        String shiftCode = shift != null ? shift.getShiftCode() : null;
        String shiftName = shift != null ? shift.getShiftName() : null;
        var startTime = shift != null ? shift.getStartTime() : null;
        var endTime = shift != null ? shift.getEndTime() : null;

        String employeeName = account != null ? account.getFullName() : null;
        String employeeEmail = account != null ? account.getEmail() : null;

        return new ShiftAssignmentResponse(
                assignment.getId(),
                assignment.getShiftId(),
                shiftCode,
                shiftName,
                startTime,
                endTime,
                assignment.getBranchId(),
                assignment.getAccountId(),
                employeeName,
                employeeEmail,
                assignment.getWorkDate(),
                assignment.getStatus(),
                assignment.getCheckInAt(),
                assignment.getCheckOutAt(),
                assignment.getInitialCash(),
                assignment.getFinalCash(),
                assignment.getCashDifference(),
                assignment.getNote(),
                assignment.getCreatedAt()
        );
    }
}
