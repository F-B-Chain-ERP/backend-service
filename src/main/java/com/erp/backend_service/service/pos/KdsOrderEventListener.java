package com.erp.backend_service.service.pos;

import com.erp.backend_service.event.KdsOrderEvent;
import com.erp.backend_service.service.KdsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class KdsOrderEventListener {
    private static final Logger log = LoggerFactory.getLogger(KdsOrderEventListener.class);
    private final KdsService kdsService;

    public KdsOrderEventListener(KdsService kdsService) {
        this.kdsService = kdsService;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(KdsOrderEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }
        try {
            switch (event.action()) {
                case CREATE -> kdsService.createOnOrderConfirmed(event.orderId());
                case SYNC -> kdsService.syncFromOrder(event.orderId(), event.orderStatus());
                case SERVE -> kdsService.markServedByOrderId(event.orderId());
                case CANCEL -> kdsService.cancelByOrderId(event.orderId(), event.reason());
            }
        } catch (Exception e) {
            // Side-effect không được rollback order đã commit; log đủ context để retry vận hành.
            log.error("KDS side-effect {} thất bại cho order {}: {}",
                event.action(), event.orderId(), e.getMessage(), e);
        }
    }
}
