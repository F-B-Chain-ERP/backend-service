package com.erp.backend_service.mapper;

import com.erp.core.domain.ReportJob;
import com.erp.core.dto.response.report.ReportJobResponse;
import com.erp.core.dto.response.report.ReportJobSummaryResponse;
import org.springframework.stereotype.Component;

/**
 * Mapper thủ công chuyển đổi giữa thực thể ReportJob và các DTO phản hồi.
 */
@Component
public class ReportJobMapper {

    public ReportJobResponse toResponse(ReportJob entity) {
        if (entity == null) {
            return null;
        }
        return new ReportJobResponse(
                entity.getId(),
                entity.getModule(),
                entity.getReportType(),
                entity.getStatus(),
                entity.getFormat(),
                entity.getRequestedBy(),
                entity.getBranchId(),
                entity.getEstimatedRows(),
                entity.getFileUrl(),
                entity.getErrorMessage(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getCreatedAt()
        );
    }

    public ReportJobSummaryResponse toSummary(ReportJob entity) {
        if (entity == null) {
            return null;
        }
        return new ReportJobSummaryResponse(
                entity.getId(),
                entity.getModule(),
                entity.getReportType(),
                entity.getStatus(),
                entity.getFormat(),
                entity.getFileUrl(),
                entity.getCompletedAt(),
                entity.getCreatedAt()
        );
    }
}
