package com.erp.backend_service.service;

import java.math.BigDecimal;

/**
 * Kết quả tính toán đầy đủ các chỉ tiêu tài chính của một ngày/chi nhánh từ dữ liệu nguồn.
 * Owned bởi {@link FinancialSummaryCalculationService} — nguồn duy nhất của công thức S5-04.
 */
public record FinancialSummaryData(
        BigDecimal grossRevenue,
        BigDecimal discountAmount,
        BigDecimal processedRefund,
        BigDecimal netRevenue,
        BigDecimal totalCogs,
        BigDecimal grossProfit,
        BigDecimal totalExpense,
        BigDecimal netProfit,
        long orderCount
) {
}