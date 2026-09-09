package com.erp.backend_service.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Service xử lý upload/delete file lên object storage (MinIO).
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
}
