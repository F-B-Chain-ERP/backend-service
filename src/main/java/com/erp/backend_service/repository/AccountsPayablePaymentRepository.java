package com.erp.backend_service.repository;

import com.erp.core.domain.AccountsPayablePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Truy vấn dữ liệu thanh toán công nợ (accounts_payable_payment).
 */
@Repository
public interface AccountsPayablePaymentRepository extends JpaRepository<AccountsPayablePayment, UUID> {

    /**
     * Lấy danh sách thanh toán của một công nợ, sắp xếp theo ngày thanh toán tăng dần.
     */
    List<AccountsPayablePayment> findByAccountsPayableIdOrderByPaymentDateAsc(UUID accountsPayableId);

    /**
     * Tổng số tiền đã thanh toán của một công nợ.
     */
    @Query("select coalesce(sum(p.amount), 0) from AccountsPayablePayment p where p.accountsPayableId = :payableId")
    BigDecimal sumAmountByAccountsPayableId(@Param("payableId") UUID payableId);

    /**
     * Đếm số bản ghi thanh toán của một công nợ.
     */
    long countByAccountsPayableId(UUID accountsPayableId);
}
