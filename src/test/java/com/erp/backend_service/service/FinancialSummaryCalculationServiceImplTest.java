package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.BranchDailyFinancialSummaryRepository;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.ExpenseRepository;
import com.erp.backend_service.repository.OrderRepository;
import com.erp.backend_service.repository.RefundRepository;
import com.erp.backend_service.service.FinancialSummaryData;
import com.erp.core.domain.Branch;
import com.erp.core.domain.BranchDailyFinancialSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test {@link FinancialSummaryCalculationServiceImpl} — owner công thức S5-04.
 */
@ExtendWith(MockitoExtension.class)
class FinancialSummaryCalculationServiceImplTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private BranchDailyFinancialSummaryRepository summaryRepository;

    private FinancialSummaryCalculationServiceImpl calculationService;
    private UUID branchId;
    private LocalDate businessDate;

    @BeforeEach
    void setUp() {
        calculationService = new FinancialSummaryCalculationServiceImpl(
                expenseRepository, orderRepository, refundRepository, branchRepository, summaryRepository);
        branchId = UUID.randomUUID();
        businessDate = LocalDate.of(2026, 9, 23);
    }

    private List<Object[]> orderAgg(long count, String subtotal, String discount, String cogs) {
        return Collections.singletonList(new Object[]{count, new BigDecimal(subtotal), new BigDecimal(discount), new BigDecimal(cogs)});
    }

    @Test
    @DisplayName("TC01: Không có dữ liệu nguồn → tất cả chỉ tiêu bằng 0")
    void testComputeNoDataReturnsZeros() {
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branchOf(branchId)));
        when(orderRepository.summarizeCompletedByBranchBetween(any(), any(), any()))
                .thenReturn(orderAgg(0, "0", "0", "0"));
        when(orderRepository.countCompletedWithMissingAmounts(any(), any(), any())).thenReturn(0L);
        when(refundRepository.sumProcessedAmountByBranchBetween(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(expenseRepository.sumActiveAmountByBranchIdAndExpenseDate(branchId, businessDate)).thenReturn(BigDecimal.ZERO);

        FinancialSummaryData data = calculationService.compute(branchId, businessDate);

        assertEquals(0L, data.orderCount());
        assertEquals(BigDecimal.ZERO, data.grossRevenue());
        assertEquals(BigDecimal.ZERO, data.netRevenue());
        assertEquals(BigDecimal.ZERO, data.totalCogs());
        assertEquals(BigDecimal.ZERO, data.grossProfit());
        assertEquals(BigDecimal.ZERO, data.totalExpense());
        assertEquals(BigDecimal.ZERO, data.netProfit());
    }

    @Test
    @DisplayName("TC02: Chỉ có Order COMPLETED → đầy đủ doanh thu, discount, COGS, netRevenue")
    void testComputeCompletedOrdersOnly() {
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branchOf(branchId)));
        when(orderRepository.summarizeCompletedByBranchBetween(any(), any(), any()))
                .thenReturn(orderAgg(2, "1000.00", "50.00", "600.00"));
        when(orderRepository.countCompletedWithMissingAmounts(any(), any(), any())).thenReturn(0L);
        when(refundRepository.sumProcessedAmountByBranchBetween(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(expenseRepository.sumActiveAmountByBranchIdAndExpenseDate(branchId, businessDate)).thenReturn(BigDecimal.ZERO);

        FinancialSummaryData data = calculationService.compute(branchId, businessDate);

        assertEquals(2L, data.orderCount());
        assertEquals(new BigDecimal("1000.00"), data.grossRevenue());
        assertEquals(new BigDecimal("50.00"), data.discountAmount());
        assertEquals(new BigDecimal("600.00"), data.totalCogs());
        assertEquals(new BigDecimal("950.00"), data.netRevenue());
        assertEquals(new BigDecimal("350.00"), data.grossProfit());
        assertEquals(BigDecimal.ZERO, data.totalExpense());
        assertEquals(new BigDecimal("350.00"), data.netProfit());
    }

    @Test
    @DisplayName("TC04/05: Refund PROCESSED trừ vào netRevenue, Expense ACTIVE trừ vào netProfit")
    void testComputeRefundAndExpenseSubtracted() {
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branchOf(branchId)));
        when(orderRepository.summarizeCompletedByBranchBetween(any(), any(), any()))
                .thenReturn(orderAgg(1, "1000.00", "0.00", "600.00"));
        when(orderRepository.countCompletedWithMissingAmounts(any(), any(), any())).thenReturn(0L);
        when(refundRepository.sumProcessedAmountByBranchBetween(any(), any(), any())).thenReturn(new BigDecimal("40.00"));
        when(expenseRepository.sumActiveAmountByBranchIdAndExpenseDate(branchId, businessDate)).thenReturn(new BigDecimal("100.00"));

        FinancialSummaryData data = calculationService.compute(branchId, businessDate);

        assertEquals(new BigDecimal("960.00"), data.netRevenue());
        assertEquals(new BigDecimal("360.00"), data.grossProfit());
        assertEquals(new BigDecimal("100.00"), data.totalExpense());
        assertEquals(new BigDecimal("260.00"), data.netProfit());
    }

    @Test
    @DisplayName("Order COMPLETED thiếu dữ liệu bắt buộc → FIN_409_SOURCE_DATA_MISSING")
    void testComputeMissingAmountsThrows() {
        when(orderRepository.summarizeCompletedByBranchBetween(any(), any(), any()))
                .thenReturn(orderAgg(3, "1000.00", "0.00", "600.00"));
        when(orderRepository.countCompletedWithMissingAmounts(any(), any(), any())).thenReturn(2L);

        BaseException ex = assertThrows(BaseException.class,
                () -> calculationService.compute(branchId, businessDate));
        assertEquals(ErrorCode.FIN_409_SOURCE_DATA_MISSING, ex.getErrorCode());
    }

    @Test
    @DisplayName("TC13: Recalculate khi chưa có summary → tạo mới DRAFT")
    void testRecalculateCreatesDraftSummary() {
        when(branchRepository.findByIdForUpdate(branchId))
                .thenReturn(Optional.of(branchOf(branchId)));
        when(summaryRepository.findByBranchIdAndBusinessDate(branchId, businessDate)).thenReturn(Optional.empty());
        when(orderRepository.summarizeCompletedByBranchBetween(any(), any(), any()))
                .thenReturn(orderAgg(1, "500.00", "30.00", "200.00"));
        when(orderRepository.countCompletedWithMissingAmounts(any(), any(), any())).thenReturn(0L);
        when(refundRepository.sumProcessedAmountByBranchBetween(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(expenseRepository.sumActiveAmountByBranchIdAndExpenseDate(branchId, businessDate)).thenReturn(new BigDecimal("50.00"));
        when(summaryRepository.save(any(BranchDailyFinancialSummary.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        BranchDailyFinancialSummary result = calculationService.recalculate(branchId, businessDate);

        assertEquals(branchId, result.getBranchId());
        assertEquals(businessDate, result.getBusinessDate());
        assertEquals("DRAFT", result.getStatus());
        assertEquals(new BigDecimal("500.00"), result.getGrossRevenue());
        assertEquals(new BigDecimal("470.00"), result.getNetRevenue());
        assertEquals(new BigDecimal("270.00"), result.getGrossProfit());
        assertEquals(new BigDecimal("50.00"), result.getTotalExpense());
        assertEquals(new BigDecimal("220.00"), result.getNetProfit());
    }

    @Test
    @DisplayName("TC13: Recalculate summary đã FINALIZED → FIN_409_SUMMARY_ALREADY_FINALIZED")
    void testRecalculateOnFinalizedThrows() {
        BranchDailyFinancialSummary finalized = summaryOf("FINALIZED");
        when(branchRepository.findByIdForUpdate(branchId))
                .thenReturn(Optional.of(branchOf(branchId)));
        when(summaryRepository.findByBranchIdAndBusinessDate(branchId, businessDate))
                .thenReturn(Optional.of(finalized));

        BaseException ex = assertThrows(BaseException.class,
                () -> calculationService.recalculate(branchId, businessDate));
        assertEquals(ErrorCode.FIN_409_SUMMARY_ALREADY_FINALIZED, ex.getErrorCode());
        verify(summaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Recalculate chi nhánh không tồn tại → FIN_404_BRANCH_NOT_FOUND")
    void testRecalculateBranchNotFoundThrows() {
        when(branchRepository.findByIdForUpdate(branchId)).thenReturn(Optional.empty());

        BaseException ex = assertThrows(BaseException.class,
                () -> calculationService.recalculate(branchId, businessDate));
        assertEquals(ErrorCode.FIN_404_BRANCH_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("calculateTotalExpense uỷ quyền đúng repository Expense ACTIVE")
    void testCalculateTotalExpenseDelegates() {
        when(expenseRepository.sumActiveAmountByBranchIdAndExpenseDate(branchId, businessDate))
                .thenReturn(new BigDecimal("250.00"));

        BigDecimal total = calculationService.calculateTotalExpense(branchId, businessDate);

        assertEquals(new BigDecimal("250.00"), total);
    }

    private Branch branchOf(UUID id) {
        Branch branch = new Branch();
        branch.setId(id);
        branch.setTimezone("Asia/Ho_Chi_Minh");
        return branch;
    }

    private BranchDailyFinancialSummary summaryOf(String status) {
        BranchDailyFinancialSummary summary = new BranchDailyFinancialSummary();
        summary.setBranchId(branchId);
        summary.setBusinessDate(businessDate);
        summary.setStatus(status);
        return summary;
    }
}