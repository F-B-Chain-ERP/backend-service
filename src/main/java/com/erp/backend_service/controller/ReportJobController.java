package com.erp.backend_service.controller;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.ReportJobService;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.report.ReportJobResponse;
import com.erp.core.dto.response.report.ReportJobSummaryResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller quản lý truy vấn và theo dõi tiến độ các tác vụ xuất báo cáo ngầm.
 */
@RestController
@RequestMapping("/api/v1/reports/jobs")
public class ReportJobController {

    private final ReportJobService reportJobService;

    public ReportJobController(ReportJobService reportJobService) {
        this.reportJobService = reportJobService;
    }

    /**
     * Lấy danh sách các tác vụ báo cáo của tài khoản đang đăng nhập.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<ReportJobSummaryResponse>>> getMyJobs(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID currentUserId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED));
        return ResponseEntity.ok(ApiResponse.success(reportJobService.listMyJobs(currentUserId, pageable)));
    }

    /**
     * Lấy thông tin trạng thái chi tiết của một tác vụ báo cáo theo ID.
     */
    @GetMapping("/{jobId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReportJobResponse>> getJobStatus(@PathVariable UUID jobId) {
        UUID currentUserId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED));
        return ResponseEntity.ok(ApiResponse.success(reportJobService.getJob(jobId, currentUserId)));
    }

    /**
     * Hủy bỏ tác vụ báo cáo nếu đang trong trạng thái chờ.
     */
    @DeleteMapping("/{jobId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> cancelJob(@PathVariable UUID jobId) {
        UUID currentUserId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED));
        reportJobService.cancelJob(jobId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã hủy tác vụ báo cáo thành công"));
    }
}
