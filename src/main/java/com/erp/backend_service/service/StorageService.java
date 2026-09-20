package com.erp.backend_service.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service xử lý upload/delete/download trên object storage (MinIO).
 */
public interface StorageService {

    /**
     * Upload một file lên MinIO và trả về URL công khai để truy cập.
     *
     * @param file      File cần upload
     * @param folder    Thư mục con trong bucket (ví dụ: "products")
     * @return URL công khai của file đã upload
     */
    String upload(MultipartFile file, String folder);

    /**
     * Xóa file khỏi MinIO theo URL công khai.
     *
     * @param fileUrl URL công khai của file cần xóa
     */
    void delete(String fileUrl);

    /**
     * Tải về một file từ MinIO theo URL công khai dưới dạng Resource để streaming tới client.
     *
     * @param fileUrl URL công khai của file cần tải về
     * @return Resource chứa nội dung file (streaming từ object storage, không tải toàn bộ vào RAM)
     */
    Resource download(String fileUrl);

    /**
     * Tải về một file báo cáo đã xuất (thuộc bucket báo cáo {@code erp-reports}, nơi
     * queue-worker đã upload), stream trực tiếp tới client.
     *
     * @param fileUrl URL công khai của file báo cáo
     * @return Resource chứa nội dung file báo cáo
     */
    Resource downloadReport(String fileUrl);

    /**
     * Xóa file báo cáo khỏi bucket báo cáo.
     *
     * @param fileUrl URL công khai của file báo cáo cần xóa
     */
    void deleteReport(String fileUrl);
}
