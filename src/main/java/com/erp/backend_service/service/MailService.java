package com.erp.backend_service.service;

import java.util.Map;

/**
 * Dịch vụ gửi email (SMTP). Tất cả phương thức đều an toàn: nếu email bị tắt
 * hoặc gửi thất bại, chỉ ghi log và không ném lỗi để không ảnh hưởng luồng nghiệp vụ.
 */
public interface MailService {

    /** Kiểm tra dịch vụ email có đang được bật hay không. */
    boolean isEnabled();

    /**
     * Gửi email dạng HTML.
     *
     * @param to      địa chỉ nhận
     * @param subject tiêu đề
     * @param htmlContent nội dung HTML
     */
    void sendHtml(String to, String subject, String htmlContent);

    /**
     * Gửi email dạng văn bản thuần.
     *
     * @param to      địa chỉ nhận
     * @param subject tiêu đề
     * @param text    nội dung văn bản
     */
    void sendText(String to, String subject, String text);

    /**
     * Render một template Thymeleaf và gửi email dạng HTML.
     * MailService tự render template bên trong; caller không cần render thủ công.
     *
     * @param to           địa chỉ nhận
     * @param subject      tiêu đề
     * @param templateName đường dẫn template (không bao gồm tiền tố/suffix,
     *                     ví dụ: {@code "mail/welcome"})
     * @param variables    biến truyền vào template
     */
    void sendTemplate(String to, String subject, String templateName, Map<String, Object> variables);
}
