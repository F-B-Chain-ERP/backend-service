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
import com.erp.core.report.ReportColumnDefinition;
import com.erp.core.report.ReportDataContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm tra điều phối routing Sync/Async của {@link ReportRequestHandler}:
 * <ul>
 *   <li>AUTO: ≤ ngưỡng Async → xử lý đồng bộ (HTTP 200); vượt ngưỡng → đưa vào hàng đợi (HTTP 202).</li>
 *   <li>SYNC: vượt giới hạn an toàn tối đa → từ chối với REPORT_400_SYNC_LIMIT_EXCEEDED.</li>
 *   <li>ASYNC: luôn tạo tác vụ và push RabbitMQ, trả về HTTP 202.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ReportThresholdRoutingTest {

    @Mock
    private ReportJobService reportJobService;
    @Mock
    private ReportMessagePublisher messagePublisher;
    @Mock
    private ExportStrategyFactory strategyFactory;
    @Mock
    private ExportStrategy strategy;
    @Mock
    private ReportJobMapper reportJobMapper;

    private ReportProperties properties;
    private ReportRequestHandler handler;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        properties = new ReportProperties();
        properties.setAsyncThresholdRecords(100);
        properties.setMaxHardSyncRecords(500);

        lenient().when(strategyFactory.getStrategy(any())).thenReturn(strategy);
        lenient().when(strategy.getContentType()).thenReturn("application/octet-stream");
        lenient().when(strategy.getFileExtension()).thenReturn(".xlsx");
        lenient().when(strategy.export(any(ReportDataContext.class))).thenReturn(new byte[]{1, 2, 3});

        handler = new ReportRequestHandler(properties, reportJobService, messagePublisher, strategyFactory, reportJobMapper);
    }

    private ResponseEntity<?> autoWithRows(int rows) {
        return handler.handleExport(
                ReportModule.POS,
                "POS_ORDER_EXPORT",
                ExportFormat.EXCEL,
                ExportReportMode.AUTO,
                USER_ID,
                BRANCH_ID,
                Collections.emptyMap(),
                () -> rows,
                () -> buildContext(),
                "BaoCaoChiTietDonHang"
        );
    }

    private ReportDataContext buildContext() {
        return new ReportDataContext(
                "BÁO CÁO TEST",
                "subtitle",
                null,
                Map.of("rowCount", 1),
                List.of(ReportColumnDefinition.text("code", "Mã", 10)),
                List.of(Map.<String, Object>of("code", "A1")),
                Map.of("label", "TỔNG CỘNG"),
                List.of()
        );
    }

    private ReportJob pendingJob() {
        ReportJob job = new ReportJob();
        job.setId(UUID.randomUUID());
        return job;
    }

    private ReportJobResponse jobResponse() {
        return new ReportJobResponse(
                UUID.randomUUID(),
                ReportModule.POS.name(),
                "POS_ORDER_EXPORT",
                "PENDING",
                ExportFormat.EXCEL.name(),
                USER_ID,
                BRANCH_ID,
                150,
                null,
                null,
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("AUTO: số dòng dưới ngưỡng Async → xử lý đồng bộ, HTTP 200, không tạo job")
    void auto_belowThreshold_runsSyncAndReturnsBytes() {
        ResponseEntity<?> response = autoWithRows(50);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(byte[].class);
        verify(strategy).export(any(ReportDataContext.class));
        verify(reportJobService, never()).createJob(any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("AUTO: số dòng vượt ngưỡng Async → tạo job, push RabbitMQ, HTTP 202")
    void auto_aboveThreshold_dispatchesAsyncJob() {
        when(reportJobService.createJob(eq(ReportModule.POS), eq("POS_ORDER_EXPORT"), eq(ExportFormat.EXCEL),
                eq(USER_ID), eq(BRANCH_ID), any(), eq(150))).thenReturn(pendingJob());
        when(reportJobMapper.toResponse(any(ReportJob.class))).thenReturn(jobResponse());

        ResponseEntity<?> response = autoWithRows(150);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isInstanceOf(ApiResponse.class);
        verify(messagePublisher).publishReportJob(any(ReportMessage.class));
        verify(strategy, never()).export(any());
    }

    @Test
    @DisplayName("SYNC: vượt giới hạn an toàn → từ chối REPORT_400_SYNC_LIMIT_EXCEEDED")
    void sync_overHardSyncLimit_isRejected() {
        assertThatThrownBy(() -> handler.handleExport(
                ReportModule.POS, "POS_ORDER_EXPORT", ExportFormat.EXCEL, ExportReportMode.SYNC,
                USER_ID, BRANCH_ID, Collections.emptyMap(),
                () -> 600, () -> buildContext(), "bao-cao"))
                .isInstanceOf(BaseException.class)
                .satisfies(ex -> assertThat(((BaseException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.REPORT_400_SYNC_LIMIT_EXCEEDED));
    }

    @Test
    @DisplayName("SYNC: trong giới hạn an toàn → HTTP 200 và xuất thẳng")
    void sync_withinHardSyncLimit_runsSync() {
        ResponseEntity<?> response = handler.handleExport(
                ReportModule.POS, "POS_ORDER_EXPORT", ExportFormat.EXCEL, ExportReportMode.SYNC,
                USER_ID, BRANCH_ID, Collections.emptyMap(),
                () -> 300, () -> buildContext(), "bao-cao");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(strategy).export(any(ReportDataContext.class));
    }

    @Test
    @DisplayName("ASYNC: luôn tạo job, push RabbitMQ và trả HTTP 202")
    void async_alwaysCreatesJobAndPublishes() {
        when(reportJobService.createJob(any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(pendingJob());
        when(reportJobMapper.toResponse(any(ReportJob.class))).thenReturn(jobResponse());

        ResponseEntity<?> response = handler.handleExport(
                ReportModule.POS, "POS_ORDER_EXPORT", ExportFormat.EXCEL, ExportReportMode.ASYNC,
                USER_ID, BRANCH_ID, Collections.emptyMap(),
                () -> 10_000, () -> buildContext(), "bao-cao");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(messagePublisher).publishReportJob(any(ReportMessage.class));
    }

    @Test
    @DisplayName("Chuẩn hoá reportType theo tên thiết kế (SALES_SUMMARY → POS_SALES_SUMMARY)")
    void asynReportType_aliasIsNormalizedToCanonicalCode() {
        when(reportJobService.createJob(any(), any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(pendingJob());
        when(reportJobMapper.toResponse(any(ReportJob.class))).thenReturn(jobResponse());

        handler.handleExport(
                ReportModule.POS, "SALES_SUMMARY", ExportFormat.EXCEL, ExportReportMode.ASYNC,
                USER_ID, BRANCH_ID, Collections.emptyMap(),
                () -> 10_000, () -> buildContext(), "bao-cao");

        ArgumentCaptor<ReportMessage> captor = ArgumentCaptor.forClass(ReportMessage.class);
        verify(messagePublisher).publishReportJob(captor.capture());
        assertThat(captor.getValue().getReportType()).isEqualTo("POS_SALES_SUMMARY");
    }

    @Test
    @DisplayName("AUTO không chỉ định chế độ → mặc định AUTO")
    void handleExport_withNullMode_defaultsToAuto() {
        ResponseEntity<?> response = handler.handleExport(
                ReportModule.POS, "POS_ORDER_EXPORT", ExportFormat.EXCEL, null,
                USER_ID, BRANCH_ID, Collections.emptyMap(),
                () -> 10, () -> buildContext(), "bao-cao");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}