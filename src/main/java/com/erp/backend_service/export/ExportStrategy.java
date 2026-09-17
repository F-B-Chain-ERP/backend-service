package com.erp.backend_service.export;

import com.erp.core.enums.ExportFormat;

/**
 * Interface Strategy Pattern định nghĩa hành vi kết xuất báo cáo thành tệp nhị phân.
 */
public interface ExportStrategy {

    /**
     * Định dạng mà strategy này hỗ trợ.
     */
    ExportFormat getSupportedFormat();

    /**
     * Xuất dữ liệu báo cáo thành mảng byte.
     *
     * @param context Ngữ cảnh chứa tiêu đề, cột và danh sách dòng dữ liệu
     * @return Mảng byte chứa nội dung tệp đã sinh
     */
    byte[] export(ReportDataContext context);

    /**
     * Kiểu MIME của tệp (ví dụ application/vnd.openxmlformats-officedocument.spreadsheetml.sheet hoặc application/pdf).
     */
    String getContentType();

    /**
     * Phần mở rộng của tệp (ví dụ .xlsx, .pdf).
     */
    String getFileExtension();
}
