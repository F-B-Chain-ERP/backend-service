package com.erp.backend_service.controller;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.security.SecurityUtils;
import com.erp.backend_service.service.ReportJobService;
import com.erp.backend_service.service.StorageService;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.report.ReportJobResponse;
import com.erp.core.dto.response.report.ReportJobSummaryResponse;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Controller quản lý truy vấn và theo dõi tiến độ các tác vụ xuất báo cáo ngầm.
 */
@RestController
@RequestMapping("/api/v1/reports/jobs")
public class ReportJobController {

    private final ReportJobService reportJobService;
    private final StorageService storageService;
    private static final Logger log = LoggerFactory.getLogger(ReportJobController.class);

    public ReportJobController(ReportJobService reportJobService, StorageService storageService) {
        this.reportJobService = reportJobService;
        this.storageService = storageService;
    }

    /**
     * Lấy danh sách các tác vụ báo cáo của tài khoản đang đăng nhập.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<ReportJobSummaryResponse>>> getMyJobs(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Get list: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
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
        log.info("Get {}", jobId);
        UUID currentUserId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED));
        return ResponseEntity.ok(ApiResponse.success(reportJobService.getJob(jobId, currentUserId)));
    }

    /**
     * Tải về file báo cáo đã hoàn thành (stream trực tiếp từ MinIO).
     */
    @GetMapping("/{jobId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> download(@PathVariable UUID jobId) {
        log.info("Download job id={}", jobId);
        UUID currentUserId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED));
        ReportJobResponse job = reportJobService.getJob(jobId, currentUserId);
        if (job.status() == null || !"DONE".equals(job.status())
                || job.fileUrl() == null || job.fileUrl().isBlank()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Tác vụ chưa hoàn thành hoặc chưa có file báo cáo");
        }
        String extension = ".xlsx";
        int dot = job.fileUrl().lastIndexOf('.');
        if (dot != -1 && dot < job.fileUrl().length() - 1) {
            extension = job.fileUrl().substring(dot);
        }
        String fileName = "bao-cao-" + jobId + extension;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(storageService.downloadReport(job.fileUrl()));
    }

    /**
     * Hủy bỏ tác vụ báo cáo nếu đang trong trạng thái chờ.
     */
    @DeleteMapping("/{jobId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> cancelJob(@PathVariable UUID jobId) {
        log.info("Cancel job id={}", jobId);
        UUID currentUserId = SecurityUtils.getCurrentPrincipalId()
                .orElseThrow(() -> new BaseException(ErrorCode.UNAUTHORIZED));
        reportJobService.cancelJob(jobId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã hủy tác vụ báo cáo thành công"));
    }
}
