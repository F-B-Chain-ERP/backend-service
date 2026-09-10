package com.erp.backend_service.service.impl;

import com.erp.backend_service.configuration.MinioProperties;
import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.service.StorageService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

/**
 * Triển khai StorageService sử dụng MinIO object storage.
 */
@Service
public class MinioStorageServiceImpl implements StorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L; // 5 MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private final MinioClient minioClient;
    private final MinioProperties props;

    public MinioStorageServiceImpl(MinioClient minioClient, MinioProperties props) {
        this.minioClient = minioClient;
        this.props = props;
    }

    @Override
    public String upload(MultipartFile file, String folder) {
        validateFile(file);
        String objectName = buildObjectName(folder, file.getOriginalFilename());
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
            throw new BaseException(ErrorCode.MENU_500_STORAGE_UPLOAD_FAILED,
                    "Không thể tải ảnh lên máy chủ lưu trữ: " + e.getMessage());
        }
        // Trả về URL công khai: {publicUrl}/{bucketName}/{objectName}
        String baseUrl = props.getPublicUrl().endsWith("/")
                ? props.getPublicUrl() : props.getPublicUrl() + "/";
        return baseUrl + props.getBucketName() + "/" + objectName;
    }

    @Override
    public void delete(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return;
        try {
            // Trích xuất objectName từ URL: tìm sau /{bucketName}/
            String bucketMarker = "/" + props.getBucketName() + "/";
            int idx = fileUrl.indexOf(bucketMarker);
            String objectName;
            if (idx != -1) {
                objectName = fileUrl.substring(idx + bucketMarker.length());
            } else {
                String prefix = props.getPublicUrl().endsWith("/")
                        ? props.getPublicUrl() + props.getBucketName() + "/"
                        : props.getPublicUrl() + "/" + props.getBucketName() + "/";
                if (!fileUrl.startsWith(prefix)) return;
                objectName = fileUrl.substring(prefix.length());
            }

            if (objectName.isBlank()) return;

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(props.getBucketName())
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            // Không ném exception khi xóa thất bại (file có thể đã bị xóa trước đó)
            System.err.println("[MinIO] Không thể xóa file '" + fileUrl + "': " + e.getMessage());
        }
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
