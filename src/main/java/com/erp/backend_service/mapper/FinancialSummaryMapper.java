package com.erp.backend_service.mapper;

import com.erp.core.domain.Branch;
import com.erp.core.domain.BranchDailyFinancialSummary;
import com.erp.core.domain.Expense;
import com.erp.core.domain.Order;
import com.erp.core.domain.Refund;
import com.erp.core.dto.response.fin.FinancialSummaryExpenseSourceResponse;
import com.erp.core.dto.response.fin.FinancialSummaryOrderSourceResponse;
import com.erp.core.dto.response.fin.FinancialSummaryRefundSourceResponse;
import com.erp.core.dto.response.fin.FinancialSummaryResponse;
import org.springframework.stereotype.Component;

/**
 * Chuyển đổi dữ liệu báo cáo tài chính (BranchDailyFinancialSummary và dữ liệu nguồn) sang response DTO.
 */
@Component
public class FinancialSummaryMapper {

    public FinancialSummaryResponse toSummaryResponse(BranchDailyFinancialSummary summary, Branch branch) {
        return new FinancialSummaryResponse(
                summary.getId().toString(),
                summary.getBranchId().toString(),
                branch != null ? branch.getName() : null,
                summary.getBusinessDate(),
                summary.getGrossRevenue(),
                summary.getDiscountAmount(),
                summary.getNetRevenue(),
                summary.getTotalCogs(),
                summary.getGrossProfit(),
                summary.getTotalExpense(),
                summary.getNetProfit(),
                summary.getOrderCount(),
                summary.getStatus(),
                summary.getCreatedAt(),
                summary.getUpdatedAt()
        );
    }

    public FinancialSummaryOrderSourceResponse toOrderSource(Order order) {
        return new FinancialSummaryOrderSourceResponse(
                order.getId().toString(),
                order.getOrderCode(),
                order.getCompletedAt(),
                order.getSubtotalAmount(),
                order.getDiscountAmount(),
                order.getTotalCogsAmount()
        );
    }

    public FinancialSummaryRefundSourceResponse toRefundSource(Refund refund) {
        return new FinancialSummaryRefundSourceResponse(
                refund.getId().toString(),
                refund.getRefundCode(),
                refund.getOrderId() != null ? refund.getOrderId().toString() : null,
                refund.getProcessedAt(),
                refund.getAmount()
        );
    }

    public FinancialSummaryExpenseSourceResponse toExpenseSource(Expense expense) {
        return new FinancialSummaryExpenseSourceResponse(
                expense.getId().toString(),
                expense.getExpenseCategory(),
                expense.getAmount(),
                expense.getExpenseDate(),
                expense.getDescription()
        );
    }
}