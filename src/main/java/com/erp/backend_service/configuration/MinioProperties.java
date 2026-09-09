package com.erp.backend_service.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình kết nối MinIO object storage.
 * Binding từ prefix {@code app.minio} trong application.yaml.
 */
@ConfigurationProperties(prefix = "app.minio")
public class MinioProperties {

    /** Địa chỉ endpoint của MinIO (ví dụ: http://localhost:9000) */
    private String endpoint;

    /** Access key (username) để xác thực */
    private String accessKey;

    /** Secret key (password) để xác thực */
    private String secretKey;

    /** Tên bucket lưu trữ ảnh sản phẩm */
    private String bucketName;

    /** URL công khai dùng để tạo link truy cập ảnh */
    private String publicUrl;

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }

    public String getPublicUrl() { return publicUrl; }
    public void setPublicUrl(String publicUrl) { this.publicUrl = publicUrl; }
}
