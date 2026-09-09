package com.erp.backend_service.configuration;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình MinioClient bean và tự động tạo bucket nếu chưa tồn tại.
 */
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfiguration {

    @Bean
    public MinioClient minioClient(MinioProperties props) {
        MinioClient client = MinioClient.builder()
                .endpoint(props.getEndpoint())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .build();
        ensureBucketExists(client, props.getBucketName());
        return client;
    }

    private void ensureBucketExists(MinioClient client, String bucketName) {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e) {
            // Ghi log nhưng không ném exception để tránh làm fail startup khi minio chưa sẵn sàng
            System.err.println("[MinIO] Không thể kiểm tra/tạo bucket '" + bucketName + "': " + e.getMessage());
        }
    }
}
