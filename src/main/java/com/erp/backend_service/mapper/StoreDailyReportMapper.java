package com.erp.backend_service.mapper;

import com.erp.core.domain.Account;
import com.erp.core.domain.StoreDailyReport;
import com.erp.core.dto.response.store.StoreDailyReportResponse;
import org.springframework.stereotype.Component;

/**
 * Ánh xạ thực thể StoreDailyReport sang StoreDailyReportResponse DTO.
 */
@Component
public class StoreDailyReportMapper {

    public StoreDailyReportResponse toResponse(StoreDailyReport report, Account submitter) {
        if (report == null) {
            return null;
        }

        String submitterName = submitter != null ? submitter.getFullName() : null;

        return new StoreDailyReportResponse(
                report.getId(),
                report.getBranchId(),
                report.getBusinessDate(),
                report.getOpeningCash(),
                report.getClosingCash(),
                report.getTotalOrders(),
                report.getGrossRevenue(),
                report.getDiscountAmount(),
                report.getNetRevenue(),
                report.getCashAmount(),
                report.getTransferAmount(),
                report.getStatus(),
                report.getSubmittedById(),
                submitterName,
                report.getSubmittedAt(),
                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }
}
