package com.erp.backend_service.service.impl;

import com.erp.backend_service.configuration.MinioProperties;
import com.erp.backend_service.configuration.ReportProperties;
import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.service.StorageService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.StatObjectArgs;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;

/**
 * Triển khai StorageService sử dụng MinIO object storage.
 *
 * <p>Phân tách hai vùng lưu trữ: bucket ảnh sản phẩm ({@code app.minio.bucket-name},
 * mặc định {@code erp-products}) và bucket báo cáo ({@code app.report.minio-bucket-name},
 * mặc định {@code erp-reports} — nơi queue-worker upload file đã xuất).</p>
 */
@Service
public class MinioStorageServiceImpl implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageServiceImpl.class);

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L; // 5 MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private final MinioClient minioClient;
    private final MinioProperties props;
    private final ReportProperties reportProperties;

    public MinioStorageServiceImpl(MinioClient minioClient, MinioProperties props, ReportProperties reportProperties) {
        this.minioClient = minioClient;
        this.props = props;
        this.reportProperties = reportProperties;
    }

    @Override
    public String upload(MultipartFile file, String folder) {
        validateFile(file);
        String objectName = buildObjectName(folder, file.getOriginalFilename());
        log.info("Upload file: object={}, size={}", objectName, file.getSize());
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(props.getBucketName())
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
        } catch (Exception e) {
            log.error("Upload file failed: object={}", objectName, e);
            throw new BaseException(ErrorCode.MENU_500_STORAGE_UPLOAD_FAILED,
                    "Không thể tải ảnh lên máy chủ lưu trữ: " + e.getMessage());
        }
        // Trả về URL công khai: {publicUrl}/{bucketName}/{objectName}
        String baseUrl = props.getPublicUrl().endsWith("/")
                ? props.getPublicUrl() : props.getPublicUrl() + "/";
        String url = baseUrl + props.getBucketName() + "/" + objectName;
        log.info("Upload file success: object={}, url={}", objectName, url);
        return url;
    }

    @Override
    public void delete(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;
        try {
            String objectName = extractObjectName(fileUrl, props.getBucketName());
            if (objectName == null || objectName.isBlank()) return;

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(props.getBucketName())
                            .object(objectName)
                            .build()
            );
            log.info("Deleted file: object={}", objectName);
        } catch (Exception e) {
            // Không ném exception khi xóa thất bại (file có thể đã bị xóa trước đó)
            System.err.println("[MinIO] Không thể xóa file '" + fileUrl + "': " + e.getMessage());
            log.error("Delete file failed: url={}", fileUrl, e);
        }
    }

    @Override
    public Resource download(String fileUrl) {
        return downloadFrom(fileUrl, props.getBucketName());
    }

    @Override
    public Resource downloadReport(String fileUrl) {
        log.info("Download report: url={}", fileUrl);
        return downloadFrom(fileUrl, reportProperties.getMinioBucketName());
    }

    @Override
    public void deleteReport(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;
        try {
            String objectName = extractObjectName(fileUrl, reportProperties.getMinioBucketName());
            if (objectName == null || objectName.isBlank()) return;

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(reportProperties.getMinioBucketName())
                            .object(objectName)
                            .build()
            );
            log.info("Deleted report file: object={}", objectName);
        } catch (Exception e) {
            // Không ném exception khi xóa thất bại (file có thể đã bị xóa trước đó)
            System.err.println("[MinIO] Không thể xóa file báo cáo '" + fileUrl + "': " + e.getMessage());
            log.error("Delete report file failed: url={}", fileUrl, e);
        }
    }

    private Resource downloadFrom(String fileUrl, String bucketName) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        String objectName = extractObjectName(fileUrl, bucketName);
        if (objectName == null || objectName.isBlank()) {
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND, "URL báo cáo không hợp lệ: " + fileUrl);
        }

        try {
            GetObjectResponse response = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            log.info("Download file success: bucket={}, object={}", bucketName, objectName);
            return new InputStreamResource(response);
        } catch (Exception e) {
            log.error("Download file failed: bucket={}, object={}", bucketName, objectName, e);
            throw new BaseException(ErrorCode.RESOURCE_NOT_FOUND,
                    "Không thể tải báo cáo từ máy chủ lưu trữ: " + e.getMessage());
        }
    }

    /**
     * Trích xuất tên đối tượng object từ URL công khai: tìm sau marker {@code /{bucketName}/}.
     */
    private String extractObjectName(String fileUrl, String bucketName) {
        String marker = "/" + bucketName + "/";
        int idx = fileUrl.indexOf(marker);
        if (idx != -1) {
            return fileUrl.substring(idx + marker.length());
        }
        String prefix = props.getPublicUrl().endsWith("/")
                ? props.getPublicUrl() + bucketName + "/"
                : props.getPublicUrl() + "/" + bucketName + "/";
        if (!fileUrl.startsWith(prefix)) return null;
        return fileUrl.substring(prefix.length());
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BaseException(ErrorCode.MENU_400_PRODUCT_IMAGE_REQUIRED);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BaseException(ErrorCode.MENU_400_PRODUCT_IMAGE_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BaseException(ErrorCode.MENU_400_PRODUCT_IMAGE_INVALID_TYPE);
        }
    }

    private String buildObjectName(String folder, String originalFilename) {
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        String folderPrefix = (folder != null && !folder.isBlank()) ? folder.trim() + "/" : "";
        return folderPrefix + UUID.randomUUID() + ext;
    }
}
