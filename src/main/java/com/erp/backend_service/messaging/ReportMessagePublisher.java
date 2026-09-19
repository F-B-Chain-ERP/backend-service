package com.erp.backend_service.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Service xuất bản thông điệp yêu cầu tạo báo cáo vào RabbitMQ.
 */
@Component
public class ReportMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(ReportMessagePublisher.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.report.exchange:erp.report.exchange}")
    private String reportExchange;

    @Value("${app.rabbitmq.report.routing-key:report.generate}")
    private String reportRoutingKey;

    public ReportMessagePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Gửi message yêu cầu xuất báo cáo vào hàng đợi RabbitMQ.
     *
     * @param message Dữ liệu thông điệp chứa jobId, module, loại báo cáo và tham số lọc
     */
    public void publishReportJob(ReportMessage message) {
        log.info("[RabbitMQ] Publishing report job ID: {}, Module: {}, Type: {}",
                message.getJobId(), message.getModule(), message.getReportType());
        rabbitTemplate.convertAndSend(
                reportExchange,
                reportRoutingKey,
                message
        );
    }
}
