package com.erp.backend_service.service;

import com.erp.core.domain.BranchDailyFinancialSummary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Service dùng chung chịu trách nhiệm duy nhất cho công thức FULL RECALCULATION của báo cáo tài chính ngày.
 * Expense (S5-03) dùng chung để tránh công thức bị duplicate.
 */
public interface FinancialSummaryCalculationService {

    /**
     * Tổng chi phí vận hành hợp lệ: Expense ACTIVE, đúng chi nhánh và đúng ngày.
     */
    BigDecimal calculateTotalExpense(UUID branchId, LocalDate businessDate);

    /**
     * Tính toán toàn bộ chỉ tiêu từ dữ liệu nguồn hiện tại (không lưu DB).
     */
    FinancialSummaryData compute(UUID branchId, LocalDate businessDate);

    /**
     * FULL RECALCULATION: tính lại toàn bộ chỉ tiêu từ dữ liệu nguồn và tạo/cập nhật summary (status = DRAFT).
     * Bảo đảm một {branchId, businessDate} chỉ có đúng một summary (UNIQUE + khóa biên trên branch).
     */
    BranchDailyFinancialSummary recalculate(UUID branchId, LocalDate businessDate);
}