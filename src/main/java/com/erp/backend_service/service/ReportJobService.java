package com.erp.backend_service.service;

import com.erp.core.domain.ReportJob;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.report.ReportJobResponse;
import com.erp.core.dto.response.report.ReportJobSummaryResponse;
import com.erp.core.enums.ExportFormat;
import com.erp.core.enums.ReportModule;
import com.erp.core.enums.ReportStatus;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

/**
 * Service quản lý vòng đời và trạng thái của các tác vụ xuất báo cáo (ReportJob).
 */
public interface ReportJobService {

    /**
     * Lấy thông tin chi tiết một tác vụ báo cáo theo ID.
     */
    ReportJobResponse getJob(UUID jobId, UUID currentUserId);

    /**
     * Lấy danh sách các tác vụ báo cáo của người dùng hiện tại, có phân trang.
     */
    PageResponse<ReportJobSummaryResponse> listMyJobs(UUID currentUserId, Pageable pageable);

    /**
     * Khởi tạo một tác vụ báo cáo mới ở trạng thái PENDING.
     */
    ReportJob createJob(ReportModule module, String reportType, ExportFormat format,
                         UUID currentUserId, UUID branchId, Map<String, Object> params, int estimatedRows);

    /**
     * Cập nhật trạng thái tác vụ báo cáo (PROCESSING, DONE, FAILED).
     */
    ReportJob updateStatus(UUID jobId, ReportStatus status, String fileUrl, String errorMessage);

    /**
     * Hủy bỏ tác vụ báo cáo nếu vẫn còn ở trạng thái PENDING.
     */
    void cancelJob(UUID jobId, UUID currentUserId);
}
