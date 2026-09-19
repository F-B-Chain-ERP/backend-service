package com.erp.backend_service.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình thuộc tính cho module báo cáo và xuất dữ liệu.
 * Binding từ prefix {@code app.report} trong application.yaml.
 */
@ConfigurationProperties(prefix = "app.report")
public class ReportProperties {

    /** Ngưỡng phân chia xử lý đồng bộ và bất đồng bộ (mặc định 500 bản ghi) */
    private int syncThreshold = 500;

    /** Tên bucket MinIO lưu trữ các file báo cáo đã xuất */
    private String minioBucketName = "erp-reports";

    /** Thời gian chờ tối đa cho tác vụ bất đồng bộ (phút) */
    private int asyncTimeoutMinutes = 30;

    public int getSyncThreshold() {
        return syncThreshold;
    }

    public void setSyncThreshold(int syncThreshold) {
        this.syncThreshold = syncThreshold;
    }

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
}
