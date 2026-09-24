package com.erp.backend_service.service;

import com.erp.backend_service.mapper.AccountsPayableMapper;
import com.erp.backend_service.mapper.PayablePaymentMapper;
import com.erp.backend_service.repository.AccountsPayablePaymentRepository;
import com.erp.backend_service.repository.AccountsPayableRepository;
import com.erp.backend_service.repository.PurchaseOrderRepository;
import com.erp.backend_service.repository.SupplierRepository;
import com.erp.backend_service.service.impl.AccountsPayableServiceImpl;
import com.erp.core.domain.AccountsPayable;
import com.erp.core.domain.AccountsPayablePayment;
import com.erp.core.dto.request.fin.CreatePayablePaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountsPayableServiceImplTest {

    @Mock
    private AccountsPayableRepository accountsPayableRepository;
    @Mock
    private AccountsPayablePaymentRepository accountsPayablePaymentRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    private AccountsPayableServiceImpl service;
    private UUID payableId;

    @BeforeEach
    void setUp() {
        service = new AccountsPayableServiceImpl(
                accountsPayableRepository,
                accountsPayablePaymentRepository,
                supplierRepository,
                purchaseOrderRepository,
                new AccountsPayableMapper(),
                new PayablePaymentMapper()
        );
        payableId = UUID.randomUUID();
    }

    private AccountsPayable payable(String status, LocalDate dueDate) {
        AccountsPayable ap = new AccountsPayable();
        ap.setInvoiceAmount(new BigDecimal("1000000"));
        ap.setPaidAmount(BigDecimal.ZERO);
        ap.setDueDate(dueDate);
        ap.setStatus(status);
        return ap;
    }

    private void stubSaves() {
        when(accountsPayablePaymentRepository.save(any())).thenAnswer(invocation -> {
            AccountsPayablePayment payment = invocation.getArgument(0);
            payment.setId(UUID.randomUUID());
            return payment;
        });
        when(accountsPayableRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }
    private CreatePayablePaymentRequest paymentReq(String amount) {
        return new CreatePayablePaymentRequest(
                LocalDate.now(), new BigDecimal(amount), "BANK_TRANSFER", null);
    }

    @Test
    @DisplayName("Trả 1 phần nợ quá hạn thì vẫn giữ OVERDUE")
    void recordPayment_PartialOverdue_KeepsOverdue() {
        AccountsPayable ap = payable("OVERDUE", LocalDate.now().minusDays(3));
        when(accountsPayableRepository.findById(payableId)).thenReturn(Optional.of(ap));
        stubSaves();

        service.recordPayment(payableId, paymentReq("400000"));

        assertEquals(new BigDecimal("400000"), ap.getPaidAmount());
        assertEquals("OVERDUE", ap.getStatus());
    }

    @Test
    @DisplayName("Trả hết nợ quá hạn thì chuyển PAID")
    void recordPayment_FullOverdue_BecomesPaid() {
        AccountsPayable ap = payable("OVERDUE", LocalDate.now().minusDays(3));
        when(accountsPayableRepository.findById(payableId)).thenReturn(Optional.of(ap));
        stubSaves();

        service.recordPayment(payableId, paymentReq("1000000"));

        assertEquals("PAID", ap.getStatus());
    }

    @Test
    @DisplayName("Trả 1 phần nợ chưa tới hạn thì PARTIALLY_PAID")
    void recordPayment_PartialNotDue_BecomesPartiallyPaid() {
        AccountsPayable ap = payable("UNPAID", LocalDate.now().plusDays(5));
        when(accountsPayableRepository.findById(payableId)).thenReturn(Optional.of(ap));
        stubSaves();

        service.recordPayment(payableId, paymentReq("400000"));

        assertEquals("PARTIALLY_PAID", ap.getStatus());
    }
}
