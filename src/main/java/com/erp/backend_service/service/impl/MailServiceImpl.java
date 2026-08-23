package com.erp.backend_service.service.impl;

import com.erp.backend_service.service.MailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * Triển khai {@link MailService} dựa trên {@link JavaMailSender} (SMTP).
 * Tự render template Thymeleaf và gửi email dạng HTML. Tuân thủ cấu hình
 * {@code spring.mail.*} và {@code app.mail.*}. Mọi thao tác đều an toàn: nếu
 * email bị tắt hoặc gửi thất bại chỉ ghi log, không ném lỗi ảnh hưởng nghiệp vụ.
 */
@Service
public class MailServiceImpl implements MailService {

    private static final Logger log = LoggerFactory.getLogger(MailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final boolean enabled;
    private final String from;
    private final String fromName;

    public MailServiceImpl(JavaMailSender mailSender,
                           SpringTemplateEngine templateEngine,
                           @Value("${app.mail.enabled:false}") boolean enabled,
                           @Value("${app.mail.from}") String from,
                           @Value("${app.mail.from-name:}") String fromName) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.enabled = enabled;
        this.from = from;
        this.fromName = fromName;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /** {@inheritDoc} */
    @Override
    public void sendHtml(String to, String subject, String htmlContent) {
        send(to, subject, htmlContent, true);
    }

    /** {@inheritDoc} */
    @Override
    public void sendText(String to, String subject, String text) {
        send(to, subject, text, false);
    }

    /** {@inheritDoc} */
    @Override
    public void sendTemplate(String to, String subject, String templateName, Map<String, Object> variables) {
        if (!enabled) {
            log.debug("Email bị tắt (app.mail.enabled=false), bỏ qua gửi template '{}' tới {}", templateName, to);
            return;
        }
        try {
            String html = render(templateName, variables);
            send(to, subject, html, true);
        } catch (Exception exception) {
            log.error("Gửi email template '{}' tới {} thất bại: {}", templateName, to, exception.getMessage());
        }
    }

    /** Render một template Thymeleaf thành chuỗi HTML. */
    private String render(String templateName, Map<String, Object> variables) {
        Context context = new Context(Locale.getDefault());
        context.setVariables(variables);
        return templateEngine.process(templateName, context);
    }

    private void send(String to, String subject, String content, boolean html) {
        if (!enabled) {
            log.debug("Email bị tắt (app.mail.enabled=false), bỏ qua gửi tới {}", to);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromName != null && !fromName.isBlank() ? fromName + " <" + from + ">" : from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, html);
            mailSender.send(message);
            log.info("Đã gửi email '{}' tới {}", subject, to);
        } catch (MessagingException exception) {
            log.error("Gửi email tới {} thất bại: {}", to, exception.getMessage());
        }
    }
}
