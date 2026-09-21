package com.erp.backend_service.mapper;

import com.erp.core.domain.AccountsPayable;
import com.erp.core.domain.PurchaseOrder;
import com.erp.core.domain.Supplier;
import com.erp.core.dto.response.fin.AccountsPayableDetailResponse;
import com.erp.core.dto.response.fin.AccountsPayableSummaryResponse;
import com.erp.core.dto.response.fin.PayablePaymentResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Chuyển đổi entity AccountsPayable sang response DTO.
 */
@Component
public class AccountsPayableMapper {

    /**
     * Ánh xạ thông tin công nợ sang response tóm tắt (dùng cho danh sách).
     */
    public AccountsPayableSummaryResponse toSummaryResponse(AccountsPayable ap,
                                                            Supplier supplier,
                                                            PurchaseOrder po,
                                                            Integer paymentTermDays,
                                                            LocalDate receivedDate) {
        return new AccountsPayableSummaryResponse(
                ap.getId().toString(),
                ap.getSupplierId() != null ? ap.getSupplierId().toString() : null,
                supplier != null ? supplier.getCode() : null,
                supplier != null ? supplier.getName() : null,
                ap.getPurchaseOrderId() != null ? ap.getPurchaseOrderId().toString() : null,
                po != null ? po.getPoCode() : null,
                ap.getInvoiceNo(),
                ap.getInvoiceAmount(),
                ap.getPaidAmount(),
                computeRemainingAmount(ap),
                receivedDate,
                ap.getDueDate(),
                paymentTermDays,
                computeInvoiceDate(ap.getDueDate(), paymentTermDays),
                ap.getStatus(),
                ap.getNote(),
                ap.getCreatedAt()
        );
    }

    /**
     * Ánh xạ thông tin công nợ sang response chi tiết (dùng cho drawer/chi tiết).
     */
    public AccountsPayableDetailResponse toDetailResponse(AccountsPayable ap,
                                                          Supplier supplier,
                                                          PurchaseOrder po,
                                                          Integer paymentTermDays,
                                                          LocalDate receivedDate,
                                                          List<PayablePaymentResponse> payments) {
        return new AccountsPayableDetailResponse(
                ap.getId().toString(),
                ap.getSupplierId() != null ? ap.getSupplierId().toString() : null,
                supplier != null ? supplier.getCode() : null,
                supplier != null ? supplier.getName() : null,
                ap.getPurchaseOrderId() != null ? ap.getPurchaseOrderId().toString() : null,
                po != null ? po.getPoCode() : null,
                ap.getInvoiceNo(),
                ap.getInvoiceAmount(),
                ap.getPaidAmount(),
                computeRemainingAmount(ap),
                receivedDate,
                ap.getDueDate(),
                paymentTermDays,
                computeInvoiceDate(ap.getDueDate(), paymentTermDays),
                ap.getStatus(),
                ap.getNote(),
                ap.getCreatedAt(),
                payments
        );
    }

    /**
     * Tính số tiền còn nợ: invoiceAmount - paidAmount.
     */
    private BigDecimal computeRemainingAmount(AccountsPayable ap) {
        BigDecimal invoice = ap.getInvoiceAmount() != null ? ap.getInvoiceAmount() : BigDecimal.ZERO;
        BigDecimal paid = ap.getPaidAmount() != null ? ap.getPaidAmount() : BigDecimal.ZERO;
        return invoice.subtract(paid);
    }

    /**
     * Tính ngày phát hóa đơn: invoiceDate = dueDate - paymentTermDays.
     * Nếu dueDate hoặc paymentTermDays null → trả về null.
     * Nếu dueDate là sentinel (9999-12-31) → trả về null (chưa có HĐ).
     */
    private LocalDate computeInvoiceDate(LocalDate dueDate, Integer paymentTermDays) {
        if (dueDate == null || paymentTermDays == null) {
            return null;
        }
        // Sentinel date = "chưa có hạn thanh toán"
        if (dueDate.getYear() >= 9999) {
            return null;
        }
        return dueDate.minusDays(paymentTermDays);
    }
}
