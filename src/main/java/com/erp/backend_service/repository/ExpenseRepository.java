package com.erp.backend_service.repository;

import com.erp.core.domain.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Repository truy xuất các khoản chi phí hoạt động (Expense).
 */
@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

    /**
     * Tổng số tiền các khoản chi phí còn hiệu lực (status = ACTIVE) của chi nhánh trong ngày.
     */
    @Query("""
            select coalesce(sum(e.amount), 0)
            from Expense e
            where e.branchId = :branchId
              and e.expenseDate = :expenseDate
              and e.status = 'ACTIVE'
            """)
    BigDecimal sumActiveAmountByBranchIdAndExpenseDate(@Param("branchId") UUID branchId,
                                                       @Param("expenseDate") LocalDate expenseDate);

    /**
     * Định khoản dữ liệu nguồn chi phí ACTIVE đúng chi nhánh/ngày dùng để đối soát tổng chi phí.
     */
    Page<Expense> findByBranchIdAndExpenseDateAndStatus(UUID branchId, LocalDate expenseDate,
                                                        String status, Pageable pageable);
}
