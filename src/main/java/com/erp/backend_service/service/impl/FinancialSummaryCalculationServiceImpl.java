package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.repository.BranchDailyFinancialSummaryRepository;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.ExpenseRepository;
import com.erp.backend_service.repository.OrderRepository;
import com.erp.backend_service.repository.RefundRepository;
import com.erp.backend_service.service.FinancialSummaryCalculationService;
import com.erp.backend_service.service.FinancialSummaryData;
import com.erp.core.domain.Branch;
import com.erp.core.domain.BranchDailyFinancialSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Triển khai {@link FinancialSummaryCalculationService}.
 *
 * <p>Owner duy nhất của công thức S5-04:
 * netRevenue = grossRevenue - discountAmount - refundPROCESSED;
 * grossProfit = netRevenue - totalCogs; netProfit = grossProfit - totalExpense.
 * Expense (S5-03) tái sử dụng {@link #calculateTotalExpense} để tránh duplicate công thức.
 */
@Service
public class FinancialSummaryCalculationServiceImpl implements FinancialSummaryCalculationService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_FINALIZED = "FINALIZED";
    private static final String DEFAULT_BRANCH_TIMEZONE = "Asia/Ho_Chi_Minh";

    private final ExpenseRepository expenseRepository;
    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final BranchRepository branchRepository;
    private final BranchDailyFinancialSummaryRepository summaryRepository;

    public FinancialSummaryCalculationServiceImpl(ExpenseRepository expenseRepository,
                                                  OrderRepository orderRepository,
                                                  RefundRepository refundRepository,
                                                  BranchRepository branchRepository,
                                                  BranchDailyFinancialSummaryRepository summaryRepository) {
        this.expenseRepository = expenseRepository;
        this.orderRepository = orderRepository;
        this.refundRepository = refundRepository;
        this.branchRepository = branchRepository;
        this.summaryRepository = summaryRepository;
    }

    /** {@inheritDoc} */
    @Override
    public BigDecimal calculateTotalExpense(UUID branchId, LocalDate businessDate) {
        return expenseRepository.sumActiveAmountByBranchIdAndExpenseDate(branchId, businessDate);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public FinancialSummaryData compute(UUID branchId, LocalDate businessDate) {
        ZoneId zone = resolveTimezone(branchId);
        Instant from = businessDate.atStartOfDay(zone).toInstant();
        Instant to = businessDate.plusDays(1).atStartOfDay(zone).toInstant();
        return computeInRange(branchId, businessDate, from, to);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public BranchDailyFinancialSummary recalculate(UUID branchId, LocalDate businessDate) {
        Branch branch = branchRepository.findByIdForUpdate(branchId)
                .orElseThrow(() -> new BaseException(ErrorCode.FIN_404_BRANCH_NOT_FOUND));

        BranchDailyFinancialSummary summary = summaryRepository
                .findByBranchIdAndBusinessDate(branchId, businessDate)
                .orElse(null);

        if (summary != null && STATUS_FINALIZED.equals(summary.getStatus())) {
            throw new BaseException(ErrorCode.FIN_409_SUMMARY_ALREADY_FINALIZED);
        }

        ZoneId zone = safeZone(branch.getTimezone());
        Instant from = businessDate.atStartOfDay(zone).toInstant();
        Instant to = businessDate.plusDays(1).atStartOfDay(zone).toInstant();
        FinancialSummaryData data = computeInRange(branchId, businessDate, from, to);

        if (summary == null) {
            summary = new BranchDailyFinancialSummary();
            summary.setBranchId(branchId);
            summary.setBusinessDate(businessDate);
            summary.setStatus(STATUS_DRAFT);
        }
        summary.setGrossRevenue(data.grossRevenue());
        summary.setDiscountAmount(data.discountAmount());
        summary.setNetRevenue(data.netRevenue());
        summary.setTotalCogs(data.totalCogs());
        summary.setGrossProfit(data.grossProfit());
        summary.setTotalExpense(data.totalExpense());
        summary.setNetProfit(data.netProfit());
        summary.setOrderCount((int) data.orderCount());
        return summaryRepository.save(summary);
    }

    private FinancialSummaryData computeInRange(UUID branchId, LocalDate businessDate, Instant from, Instant to) {
        List<Object[]> orderAggs = orderRepository.summarizeCompletedByBranchBetween(branchId, from, to);
        Object[] orderAgg = orderAggs.isEmpty() ? null : orderAggs.get(0);
        long orderCount = toLong(orderAgg != null ? orderAgg[0] : 0L);
        BigDecimal grossRevenue = toDecimal(orderAgg != null ? orderAgg[1] : BigDecimal.ZERO);
        BigDecimal discountAmount = toDecimal(orderAgg != null ? orderAgg[2] : BigDecimal.ZERO);
        BigDecimal totalCogs = toDecimal(orderAgg != null ? orderAgg[3] : BigDecimal.ZERO);

        long missing = orderRepository.countCompletedWithMissingAmounts(branchId, from, to);
        if (missing > 0) {
            throw new BaseException(ErrorCode.FIN_409_SOURCE_DATA_MISSING,
                    "Có " + missing + " đơn COMPLETED thiếu dữ liệu doanh thu/giá vốn bắt buộc");
        }

        BigDecimal processedRefund = refundRepository.sumProcessedAmountByBranchBetween(branchId, from, to);

        BigDecimal netRevenue = grossRevenue.subtract(discountAmount).subtract(processedRefund);
        BigDecimal grossProfit = netRevenue.subtract(totalCogs);
        BigDecimal totalExpense = calculateTotalExpense(branchId, businessDate);
        BigDecimal netProfit = grossProfit.subtract(totalExpense);

        return new FinancialSummaryData(grossRevenue, discountAmount, processedRefund,
                netRevenue, totalCogs, grossProfit, totalExpense, netProfit, orderCount);
    }

    private ZoneId resolveTimezone(UUID branchId) {
        Branch branch = branchRepository.findById(branchId).orElse(null);
        return branch != null ? safeZone(branch.getTimezone()) : ZoneId.of(DEFAULT_BRANCH_TIMEZONE);
    }

    private static ZoneId safeZone(String timezone) {
        try {
            return ZoneId.of(StringUtils.hasText(timezone) ? timezone : DEFAULT_BRANCH_TIMEZONE);
        } catch (DateTimeException e) {
            return ZoneId.of(DEFAULT_BRANCH_TIMEZONE);
        }
    }

    private static long toLong(Object value) {
        return ((Number) value).longValue();
    }

    private static BigDecimal toDecimal(Object value) {
        return value instanceof BigDecimal bd ? bd : new BigDecimal(String.valueOf(value));
    }
}