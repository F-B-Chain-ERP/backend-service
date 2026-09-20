package com.erp.backend_service.configuration;

import com.erp.core.constants.ReportExportConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình thuộc tính cho module báo cáo và xuất dữ liệu.
 * Binding từ prefix {@code app.report} trong application.yaml.
 *
 * <p>Các thuộc tính tại đây là nguồn dữ liệu cho {@code GET /api/v1/reports/config}
 * (không cần hard-code trùng ở Frontend).</p>
 */
@ConfigurationProperties(prefix = "app.report")
public class ReportProperties {

    /** Ngưỡng chuyển Async: từ số bản ghi này trở lên sẽ xử lý bất đồng bộ */
    private int asyncThresholdRecords = ReportExportConstants.DEFAULT_ASYNC_THRESHOLD_RECORDS;

    /** Tên bucket MinIO lưu trữ các file báo cáo đã xuất */
    private String minioBucketName = "erp-reports";

    /** Thời gian chờ tối đa cho tác vụ bất đồng bộ (phút) */
    private int asyncTimeoutMinutes = 30;

    /** Giới hạn an toàn tối đa cho luồng xử lý đồng bộ (chống OOM) */
    private int maxHardSyncRecords = ReportExportConstants.MAX_HARD_SYNC_RECORDS;

    /** Đường dẫn file logo thương hiệu nhúng vào header Excel/PDF */
    private String logoPath = ReportExportConstants.DEFAULT_LOGO_PATH;

    /** Chu kỳ polling của Frontend khi theo dõi trạng thái tác vụ Async (ms) */
    private int pollIntervalMs = ReportExportConstants.DEFAULT_POLL_INTERVAL_MS;

    /** Bất đồng bộ có đẩy trạng thái realtime qua SSE hay không */
    private boolean sseEnabled = false;

    public String getMinioBucketName() {
        return minioBucketName;
    }

    public void setMinioBucketName(String minioBucketName) {
        this.minioBucketName = minioBucketName;
    }

    public int getAsyncTimeoutMinutes() {
        return asyncTimeoutMinutes;
    }

    public void setAsyncTimeoutMinutes(int asyncTimeoutMinutes) {
        this.asyncTimeoutMinutes = asyncTimeoutMinutes;
    }

    public int getAsyncThresholdRecords() {
        return asyncThresholdRecords;
    }

    public void setAsyncThresholdRecords(int asyncThresholdRecords) {
        this.asyncThresholdRecords = asyncThresholdRecords;
    }

    public int getMaxHardSyncRecords() {
        return maxHardSyncRecords;
    }

    public void setMaxHardSyncRecords(int maxHardSyncRecords) {
        this.maxHardSyncRecords = maxHardSyncRecords;
    }

    public String getLogoPath() {
        return logoPath;
    }

    public void setLogoPath(String logoPath) {
        this.logoPath = logoPath;
    }

    public int getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(int pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public boolean isSseEnabled() {
        return sseEnabled;
    }

    public void setSseEnabled(boolean sseEnabled) {
        this.sseEnabled = sseEnabled;
    }
}