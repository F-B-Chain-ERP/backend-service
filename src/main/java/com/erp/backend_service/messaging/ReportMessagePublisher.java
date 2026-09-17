package com.erp.backend_service.messaging;

import com.erp.backend_service.configuration.RabbitMQProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Service xuất bản thông điệp yêu cầu tạo báo cáo vào RabbitMQ.
 */
@Component
public class ReportMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(ReportMessagePublisher.class);

    private final RabbitTemplate rabbitTemplate;

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
                RabbitMQProducerConfig.REPORT_EXCHANGE,
                RabbitMQProducerConfig.REPORT_ROUTING_KEY,
                message
        );
    }
}
