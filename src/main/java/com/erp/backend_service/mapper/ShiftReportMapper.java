package com.erp.backend_service.mapper;

import com.erp.core.domain.Account;
import com.erp.core.domain.ShiftReport;
import com.erp.core.dto.response.store.ShiftReportResponse;
import org.springframework.stereotype.Component;

/**
 * Ánh xạ thực thể ShiftReport sang ShiftReportResponse DTO.
 */
@Component
public class ShiftReportMapper {

    public ShiftReportResponse toResponse(ShiftReport report, Account submitter, Account approver) {
        if (report == null) {
            return null;
        }

        String submitterName = submitter != null ? submitter.getFullName() : null;
        String approverName = approver != null ? approver.getFullName() : null;

        return new ShiftReportResponse(
                report.getId(),
                report.getAssignmentId(),
                report.getBranchId(),
                report.getBusinessDate(),
                report.getInitialCash(),
                report.getCashSales(),
                report.getCardSales(),
                report.getBankTransferSales(),
                report.getEwalletSales(),
                report.getTotalSales(),
                report.getOrdersCount(),
                report.getCashPayout(),
                report.getExpectedCash(),
                report.getActualCash(),
                report.getDifference(),
                report.getDifferenceReason(),
                report.getCashDenominations(),
                report.getStatus(),
                report.getSubmittedById(),
                submitterName,
                report.getSubmittedAt(),
                report.getApprovedById(),
                approverName,
                report.getApprovedAt(),
                report.getNote(),
                report.getCreatedAt()
        );
    }
}
