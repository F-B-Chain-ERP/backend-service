package com.erp.backend_service.service.report;

import com.erp.backend_service.configuration.ReportProperties;
import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.export.ExportStrategy;
import com.erp.backend_service.export.ExportStrategyFactory;
import com.erp.backend_service.mapper.ReportJobMapper;
import com.erp.backend_service.messaging.ReportMessage;
import com.erp.backend_service.messaging.ReportMessagePublisher;
import com.erp.backend_service.service.ReportJobService;
import com.erp.core.domain.ReportJob;
import com.erp.core.dto.response.ApiResponse;
import com.erp.core.dto.response.report.ReportJobResponse;
import com.erp.core.enums.ExportFormat;
import com.erp.core.enums.ExportReportMode;
import com.erp.core.enums.ReportModule;
import com.erp.core.enums.ReportType;
import com.erp.core.report.ReportDataContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Bộ xử lý trung tâm điều phối yêu cầu xuất báo cáo:
 * - {@code AUTO}: số dòng <= ngưỡng chuyển Async (mặc định 100) xử lý đồng bộ (HTTP 200), ngược lại bất đồng bộ (HTTP 202).
 * - {@code SYNC}: ép xử lý đồng bộ; nếu số dòng vượt quá giới hạn an toàn (mặc định 500) sẽ bị từ chối.
 * - {@code ASYNC}: luôn tạo tác vụ PENDING, đẩy message vào RabbitMQ và trả về thông tin tác vụ (HTTP 202).
 */
@Component
public class ReportRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(ReportRequestHandler.class);
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ReportProperties reportProperties;
    private final ReportJobService reportJobService;
    private final ReportMessagePublisher messagePublisher;
    private final ExportStrategyFactory strategyFactory;
    private final ReportJobMapper reportJobMapper;

    public ReportRequestHandler(ReportProperties reportProperties,
                                ReportJobService reportJobService,
                                ReportMessagePublisher messagePublisher,
                                ExportStrategyFactory strategyFactory,
                                ReportJobMapper reportJobMapper) {
        this.reportProperties = reportProperties;
        this.reportJobService = reportJobService;
        this.messagePublisher = messagePublisher;
        this.strategyFactory = strategyFactory;
        this.reportJobMapper = reportJobMapper;
    }

    /**
     * Điều phối xuất báo cáo đồng bộ hoặc bất đồng bộ theo {@code mode}.
     *
     * @param module        Phân hệ (STORE, POS, PROC, INV, FIN, SYSTEM)
     * @param reportType    Mã loại báo cáo
     * @param format        Định dạng xuất (EXCEL, PDF)
     * @param mode          Chế độ điều phối (AUTO, SYNC, ASYNC) — null được xem như AUTO
     * @param currentUserId ID người dùng yêu cầu
     * @param branchId      ID chi nhánh (nếu có)
     * @param params        Các tham số lọc
     * @param countSupplier Hàm đếm nhanh số lượng dòng dữ liệu dự kiến
     * @param dataSupplier  Hàm lấy dữ liệu đầy đủ cho kết xuất đồng bộ
     * @param baseFileName  Tên tệp cơ sở (sẽ thêm timestamp và phần mở rộng)
     * @return ResponseEntity chứa byte[] (Sync) hoặc ApiResponse<ReportJobResponse> (Async)
     */
    public ResponseEntity<?> handleExport(
            ReportModule module,
            String reportType,
            ExportFormat format,
            ExportReportMode mode,
            UUID currentUserId,
            UUID branchId,
            Map<String, Object> params,
            IntSupplier countSupplier,
            Supplier<ReportDataContext> dataSupplier,
            String baseFileName
    ) {
        if (format == null) {
            format = ExportFormat.EXCEL;
        }
        if (mode == null) {
            mode = ExportReportMode.AUTO;
        }

        // Chuẩn hoá reportType về mã chuẩn (chấp nhận cả tên thiết kế như ORDER_LIST/SALES_SUMMARY).
        reportType = ReportType.normalize(reportType);

        int estimatedRows = countSupplier.getAsInt();
        int asyncThreshold = reportProperties.getAsyncThresholdRecords();
        int maxHardSync = reportProperties.getMaxHardSyncRecords();

        boolean sync = switch (mode) {
            case SYNC -> {
                if (estimatedRows > maxHardSync) {
                    log.warn("[Report] mode=SYNC rejected: estimated rows ({}) > max hard sync ({}).",
                            estimatedRows, maxHardSync);
                    throw new BaseException(ErrorCode.REPORT_400_SYNC_LIMIT_EXCEEDED);
                }
                yield true;
            }
            case ASYNC -> false;
            default -> estimatedRows < asyncThreshold;
        };

        log.info("[Report] Export request module: {}, type: {}, format: {}, mode: {}, estimated rows: {}, async threshold: {}, max sync: {}",
                module, reportType, format, mode, estimatedRows, asyncThreshold, maxHardSync);

        if (sync) {
            // === LUỒNG ĐỒNG BỘ (SYNC) ===
            log.info("[Report] Running SYNCHRONOUS export for {} rows.", estimatedRows);
            ExportStrategy strategy = strategyFactory.getStrategy(format);
            ReportDataContext context = dataSupplier.get();
            byte[] fileBytes = strategy.export(context);

            String timestamp = LocalDateTime.now().format(FILE_DATE_FORMAT);
            String fileName = baseFileName + "_" + timestamp + strategy.getFileExtension();
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(strategy.getContentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                    .header("X-Report-Mode", "SYNC")
                    .body(fileBytes);
        } else {
            // === LUỒNG BẤT ĐỒNG BỘ (ASYNC) ===
            log.info("[Report] Dispatching ASYNCHRONOUS job to RabbitMQ for {} rows.", estimatedRows);
            ReportJob job = reportJobService.createJob(module, reportType, format, currentUserId, branchId, params, estimatedRows);

            ReportMessage message = new ReportMessage(
                    job.getId(),
                    module.name(),
                    reportType,
                    format.name(),
                    currentUserId,
                    branchId,
                    params,
                    Instant.now()
            );

            messagePublisher.publishReportJob(message);

            ReportJobResponse response = reportJobMapper.toResponse(job);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .header("X-Report-Mode", "ASYNC")
                    .body(ApiResponse.success(response, "Yêu cầu báo cáo lớn đã được tiếp nhận vào hàng đợi xử lý ngầm"));
        }
    }
}
