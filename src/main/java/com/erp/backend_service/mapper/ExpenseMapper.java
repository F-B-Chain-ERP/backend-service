package com.erp.backend_service.mapper;

import com.erp.core.domain.Branch;
import com.erp.core.domain.Expense;
import com.erp.core.dto.response.fin.ExpenseResponse;
import org.springframework.stereotype.Component;

/**
 * Chuyển đổi entity Expense sang response DTO.
 */
@Component
public class ExpenseMapper {

    /**
     * Ánh xạ khoản chi phí sang response (kèm tên chi nhánh để hiển thị).
     */
    public ExpenseResponse toResponse(Expense expense, Branch branch) {
        return new ExpenseResponse(
                expense.getId().toString(),
                expense.getBranchId() != null ? expense.getBranchId().toString() : null,
                branch != null ? branch.getName() : null,
                expense.getExpenseDate(),
                expense.getExpenseCategory(),
                expense.getAmount(),
                expense.getDescription(),
                expense.getStatus(),
                expense.getCreatedAt(),
                expense.getUpdatedAt()
        );
    }
}