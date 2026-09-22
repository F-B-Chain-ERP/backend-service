package com.erp.backend_service.mapper;

import com.erp.core.domain.AccountsPayablePayment;
import com.erp.core.dto.response.fin.PayablePaymentResponse;
import org.springframework.stereotype.Component;

/**
 * Chuyển đổi entity AccountsPayablePayment sang PayablePaymentResponse.
 */
@Component
public class PayablePaymentMapper {

    /**
     * Ánh xạ thông tin thanh toán sang response.
     */
    public PayablePaymentResponse toResponse(AccountsPayablePayment payment) {
        return new PayablePaymentResponse(
                payment.getId().toString(),
                payment.getPaymentDate(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getReferenceNo(),
                payment.getCreatedBy(),
                payment.getCreatedAt()
        );
    }
}
