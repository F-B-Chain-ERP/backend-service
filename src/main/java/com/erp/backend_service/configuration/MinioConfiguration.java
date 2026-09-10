package com.erp.backend_service.configuration;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
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
            // Thiết lập policy Read-Only công khai để cho phép đọc file ảnh qua reverse proxy /storage
            String readOnlyPolicy = """
                    {
                        "Version": "2012-10-17",
                        "Statement": [
                            {
                                "Effect": "Allow",
                                "Principal": "*",
                                "Action": ["s3:GetObject"],
                                "Resource": ["arn:aws:s3:::%s/*"]
                            }
                        ]
                    }
                    """.formatted(bucketName);
            client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucketName).config(readOnlyPolicy).build());
        } catch (Exception e) {
            // Ghi log nhưng không ném exception để tránh làm fail startup khi minio chưa sẵn sàng
            System.err.println("[MinIO] Không thể kiểm tra/tạo bucket hoặc thiết lập policy '" + bucketName + "': " + e.getMessage());
        }
    }
}
