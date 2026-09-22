package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.ReportJobMapper;
import com.erp.backend_service.repository.ReportJobRepository;
import com.erp.backend_service.service.ReportJobService;
import com.erp.core.domain.ReportJob;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.report.ReportJobResponse;
import com.erp.core.dto.response.report.ReportJobSummaryResponse;
import com.erp.core.enums.ExportFormat;
import com.erp.core.enums.ReportModule;
import com.erp.core.enums.ReportStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Hiện thực dịch vụ quản lý vòng đời của tác vụ xuất báo cáo.
 */
@Service
public class ReportJobServiceImpl implements ReportJobService {

    private static final Logger log = LoggerFactory.getLogger(ReportJobServiceImpl.class);

    private final ReportJobRepository reportJobRepository;
    private final ReportJobMapper reportJobMapper;
    private final ObjectMapper objectMapper;

    public ReportJobServiceImpl(ReportJobRepository reportJobRepository,
                                ReportJobMapper reportJobMapper,
                                ObjectMapper objectMapper) {
        this.reportJobRepository = reportJobRepository;
        this.reportJobMapper = reportJobMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public ReportJobResponse getJob(UUID jobId, UUID currentUserId) {
        log.info("Get report job id={}, userId={}", jobId, currentUserId);
        ReportJob job = reportJobRepository.findByIdAndRequestedBy(jobId, currentUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tác vụ báo cáo hoặc bạn không có quyền xem"));
        return reportJobMapper.toResponse(job);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportJobSummaryResponse> listMyJobs(UUID currentUserId, Pageable pageable) {
        log.info("List report jobs: userId={}, page={}, size={}", currentUserId, pageable.getPageNumber(), pageable.getPageSize());
        Page<ReportJob> page = reportJobRepository.findByRequestedByOrderByCreatedAtDesc(currentUserId, pageable);
        return new PageResponse<>(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getContent().stream().map(reportJobMapper::toSummary).toList()
        );
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public ReportJob createJob(ReportModule module, String reportType, ExportFormat format,
                               UUID currentUserId, UUID branchId, Map<String, Object> params, int estimatedRows) {
        ReportJob job = new ReportJob();
        job.setModule(module != null ? module.name() : "SYSTEM");
        job.setReportType(reportType);
        job.setFormat(format != null ? format.name() : "EXCEL");
        job.setRequestedBy(currentUserId);
        job.setBranchId(branchId);
        job.setStatus(ReportStatus.PENDING.name());
        job.setEstimatedRows(estimatedRows);

        if (params != null && !params.isEmpty()) {
            try {
                job.setRequestParams(objectMapper.writeValueAsString(params));
            } catch (Exception e) {
                log.warn("Không thể tuần tự hóa params sang JSON: {}", e.getMessage());
            }
        }

        return reportJobRepository.save(job);
    }

    @Override
    @Transactional
    public ReportJob updateStatus(UUID jobId, ReportStatus status, String fileUrl, String errorMessage) {
        log.info("Update report job status: jobId={}, status={}", jobId, status);
        ReportJob job = reportJobRepository.findById(jobId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tác vụ báo cáo: " + jobId));

        job.setStatus(status.name());

        if (status == ReportStatus.PROCESSING && job.getStartedAt() == null) {
            job.setStartedAt(Instant.now());
        } else if (status == ReportStatus.DONE || status == ReportStatus.FAILED) {
            job.setCompletedAt(Instant.now());
        }

        if (fileUrl != null) {
            job.setFileUrl(fileUrl);
        }
        if (errorMessage != null) {
            job.setErrorMessage(errorMessage);
        }

        job = reportJobRepository.save(job);
        log.info("Report job status updated: jobId={}, status={}", job.getId(), job.getStatus());
        return job;
    }

    @Override
    @Transactional
    public void cancelJob(UUID jobId, UUID currentUserId) {
        log.info("Cancel report job id={}, userId={}", jobId, currentUserId);
        ReportJob job = reportJobRepository.findByIdAndRequestedBy(jobId, currentUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tác vụ báo cáo"));

        if (!ReportStatus.PENDING.name().equalsIgnoreCase(job.getStatus())) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Chỉ có thể hủy tác vụ khi đang ở trạng thái chờ (PENDING)");
        }

        job.setStatus("CANCELLED");
        job.setErrorMessage("Người dùng đã hủy tác vụ");
        job.setCancelledAt(Instant.now());
        job.setCompletedAt(Instant.now());
        reportJobRepository.save(job);
    }
}
