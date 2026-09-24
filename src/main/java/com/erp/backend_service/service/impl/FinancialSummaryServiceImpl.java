package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.FinancialSummaryMapper;
import com.erp.backend_service.repository.BranchDailyFinancialSummaryRepository;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.ExpenseRepository;
import com.erp.backend_service.repository.OrderRepository;
import com.erp.backend_service.repository.RefundRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.FinancialSummaryCalculationService;
import com.erp.backend_service.service.FinancialSummaryData;
import com.erp.backend_service.service.FinancialSummaryService;
import com.erp.core.domain.Branch;
import com.erp.core.domain.BranchDailyFinancialSummary;
import com.erp.core.domain.Expense;
import com.erp.core.domain.Order;
import com.erp.core.domain.Refund;
import com.erp.core.dto.request.fin.RecalculateFinancialSummaryRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.fin.FinancialSummaryExpenseSourceResponse;
import com.erp.core.dto.response.fin.FinancialSummaryOrderSourceResponse;
import com.erp.core.dto.response.fin.FinancialSummaryRefundSourceResponse;
import com.erp.core.dto.response.fin.FinancialSummaryResponse;
import com.erp.core.dto.response.fin.FinancialSummarySourceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Triển khai {@link FinancialSummaryService}: nghiệp vụ báo cáo tài chính ngày (S5-04).
 * Tính toán được uỷ quyền toàn bộ cho {@link FinancialSummaryCalculationService} (owner công thức duy nhất).
 */
@Service
public class FinancialSummaryServiceImpl implements FinancialSummaryService {

    private static final String SOURCE_ORDERS = "ORDERS";
    private static final String SOURCE_REFUNDS = "REFUNDS";
    private static final String SOURCE_EXPENSES = "EXPENSES";
    private static final Set<String> SOURCES = Set.of(SOURCE_ORDERS, SOURCE_REFUNDS, SOURCE_EXPENSES);

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_FINALIZED = "FINALIZED";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String DEFAULT_BRANCH_TIMEZONE = "Asia/Ho_Chi_Minh";

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE_COLUMNS = Set.of(
            "businessDate", "grossRevenue", "discountAmount", "netRevenue", "totalCogs",
            "grossProfit", "totalExpense", "netProfit", "orderCount", "status", "createdAt");

    private final BranchDailyFinancialSummaryRepository summaryRepository;
    private final BranchRepository branchRepository;
    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final ExpenseRepository expenseRepository;
    private final FinancialSummaryMapper mapper;
    private final DataScopeHelper dataScopeHelper;
    private final FinancialSummaryCalculationService calculationService;

    public FinancialSummaryServiceImpl(BranchDailyFinancialSummaryRepository summaryRepository,
                                       BranchRepository branchRepository,
                                       OrderRepository orderRepository,
                                       RefundRepository refundRepository,
                                       ExpenseRepository expenseRepository,
                                       FinancialSummaryMapper mapper,
                                       DataScopeHelper dataScopeHelper,
                                       FinancialSummaryCalculationService calculationService) {
        this.summaryRepository = summaryRepository;
        this.branchRepository = branchRepository;
        this.orderRepository = orderRepository;
        this.refundRepository = refundRepository;
        this.expenseRepository = expenseRepository;
        this.mapper = mapper;
        this.dataScopeHelper = dataScopeHelper;
        this.calculationService = calculationService;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<FinancialSummaryResponse> list(UUID branchId, LocalDate fromDate, LocalDate toDate,
                                                       String status, int page, int size,
                                                       String sortBy, String sortDir) {
        validatePeriod(fromDate, toDate);
        String cleanStatus = cleanStatus(status);
        if (branchId != null && !branchRepository.existsById(branchId)) {
            throw new BaseException(ErrorCode.FIN_404_BRANCH_NOT_FOUND);
        }
        UUID effectiveBranchId = resolveScopeBranch(branchId);

        int pageIdx = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String sortProperty = SORTABLE_COLUMNS.contains(sortBy) ? sortBy : "businessDate";
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(sortProperty).ascending() : Sort.by(sortProperty).descending();
        Pageable pageable = PageRequest.of(pageIdx, pageSize, sort);

        Specification<BranchDailyFinancialSummary> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (effectiveBranchId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("branchId"), effectiveBranchId));
            }
            if (fromDate != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("businessDate"), fromDate));
            }
            if (toDate != null) {
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(root.get("businessDate"), toDate));
            }
            if (cleanStatus != null) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), cleanStatus));
            }
            return predicates;
        };

        Page<BranchDailyFinancialSummary> summaryPage = summaryRepository.findAll(spec, pageable);
        Map<UUID, Branch> branchMap = branchMapOf(summaryPage.getContent());
        List<FinancialSummaryResponse> content = summaryPage.getContent().stream()
                .map(s -> mapper.toSummaryResponse(s, branchMap.get(s.getBranchId())))
                .toList();

        return new PageResponse<>(
                summaryPage.getNumber(),
                summaryPage.getSize(),
                summaryPage.getTotalElements(),
                summaryPage.getTotalPages(),
                content
        );
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public FinancialSummaryResponse get(UUID id) {
        BranchDailyFinancialSummary summary = findRequired(id);
        enforceScope(summary.getBranchId());
        return toResponse(summary);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public FinancialSummarySourceResponse<?> getSources(UUID id, String source, int page, int size) {
        BranchDailyFinancialSummary summary = findRequired(id);
        enforceScope(summary.getBranchId());

        if (!SOURCES.contains(source)) {
            throw new BaseException(ErrorCode.INVALID_REQUEST,
                    "Tham số source không hợp lệ, chỉ chấp nhận ORDERS | REFUNDS | EXPENSES");
        }

        int pageIdx = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(pageIdx, pageSize);

        return switch (source) {
            case SOURCE_ORDERS -> ordersSource(summary, pageable);
            case SOURCE_REFUNDS -> refundsSource(summary, pageable);
            default -> expensesSource(summary, pageable);
        };
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public FinancialSummaryResponse recalculate(RecalculateFinancialSummaryRequest request) {
        if (request.branchId() == null || request.businessDate() == null) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "branchId và businessDate là bắt buộc");
        }
        if (!branchRepository.existsById(request.branchId())) {
            throw new BaseException(ErrorCode.FIN_404_BRANCH_NOT_FOUND);
        }
        enforceScope(request.branchId());

        BranchDailyFinancialSummary summary = calculationService.recalculate(request.branchId(), request.businessDate());
        return toResponse(summary);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public FinancialSummaryResponse finalize(UUID id) {
        BranchDailyFinancialSummary summary = findRequired(id);
        enforceScope(summary.getBranchId());

        if (STATUS_FINALIZED.equals(summary.getStatus())) {
            throw new BaseException(ErrorCode.FIN_409_SUMMARY_ALREADY_FINALIZED);
        }
        if (!STATUS_DRAFT.equals(summary.getStatus())) {
            throw new BaseException(ErrorCode.INVALID_REQUEST, "Chỉ có thể chốt báo cáo đang ở trạng thái DRAFT");
        }

        FinancialSummaryData data = calculationService.compute(summary.getBranchId(), summary.getBusinessDate());
        if (!matches(summary, data)) {
            throw new BaseException(ErrorCode.FIN_409_RECONCILIATION_MISMATCH);
        }

        summary.setStatus(STATUS_FINALIZED);
        BranchDailyFinancialSummary saved = summaryRepository.save(summary);
        return toResponse(saved);
    }

    // ── Private helpers ───────────────────────────────────────────────

    private FinancialSummarySourceResponse<?> ordersSource(BranchDailyFinancialSummary summary, Pageable pageable) {
        ZoneId zone = branchZone(summary.getBranchId());
        Instant from = summary.getBusinessDate().atStartOfDay(zone).toInstant();
        Instant to = summary.getBusinessDate().plusDays(1).atStartOfDay(zone).toInstant();
        Page<Order> orders = orderRepository.findCompletedByBranchBetween(summary.getBranchId(), from, to, pageable);
        return FinancialSummarySourceResponse.of(SOURCE_ORDERS, pageContent(orders));
    }

    private FinancialSummarySourceResponse<?> refundsSource(BranchDailyFinancialSummary summary, Pageable pageable) {
        ZoneId zone = branchZone(summary.getBranchId());
        Instant from = summary.getBusinessDate().atStartOfDay(zone).toInstant();
        Instant to = summary.getBusinessDate().plusDays(1).atStartOfDay(zone).toInstant();
        Page<Refund> refunds = refundRepository.findProcessedByBranchBetween(summary.getBranchId(), from, to, pageable);
        return FinancialSummarySourceResponse.of(SOURCE_REFUNDS, pageContent(refunds));
    }

    private FinancialSummarySourceResponse<?> expensesSource(BranchDailyFinancialSummary summary, Pageable pageable) {
        Page<Expense> expenses = expenseRepository.findByBranchIdAndExpenseDateAndStatus(
                summary.getBranchId(), summary.getBusinessDate(), STATUS_ACTIVE, pageable);
        return FinancialSummarySourceResponse.of(SOURCE_EXPENSES, pageContent(expenses));
    }

    private <T> PageResponse<T> pageContent(Page<T> page) {
        return new PageResponse<>(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getContent()
        );
    }

    private boolean matches(BranchDailyFinancialSummary summary, FinancialSummaryData data) {
        return equalsAmount(summary.getGrossRevenue(), data.grossRevenue())
                && equalsAmount(summary.getDiscountAmount(), data.discountAmount())
                && equalsAmount(summary.getNetRevenue(), data.netRevenue())
                && equalsAmount(summary.getTotalCogs(), data.totalCogs())
                && equalsAmount(summary.getGrossProfit(), data.grossProfit())
                && equalsAmount(summary.getTotalExpense(), data.totalExpense())
                && equalsAmount(summary.getNetProfit(), data.netProfit())
                && summary.getOrderCount() != null && summary.getOrderCount() == data.orderCount();
    }

    private static boolean equalsAmount(BigDecimal stored, BigDecimal computed) {
        return stored != null && computed != null && stored.compareTo(computed) == 0;
    }

    private void validatePeriod(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BaseException(ErrorCode.FIN_400_INVALID_PERIOD);
        }
    }

    private String cleanStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String trimmed = status.trim();
        if (!STATUS_DRAFT.equals(trimmed) && !STATUS_FINALIZED.equals(trimmed)) {
            throw new BaseException(ErrorCode.INVALID_REQUEST,
                    "Trạng thái không hợp lệ, chỉ chấp nhận DRAFT hoặc FINALIZED");
        }
        return trimmed;
    }

    private BranchDailyFinancialSummary findRequired(UUID id) {
        return summaryRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.FIN_404_FINANCIAL_SUMMARY_NOT_FOUND));
    }

    private FinancialSummaryResponse toResponse(BranchDailyFinancialSummary summary) {
        Branch branch = branchRepository.findById(summary.getBranchId()).orElse(null);
        return mapper.toSummaryResponse(summary, branch);
    }

    private Map<UUID, Branch> branchMapOf(List<BranchDailyFinancialSummary> summaries) {
        Set<UUID> branchIds = summaries.stream()
                .map(BranchDailyFinancialSummary::getBranchId)
                .collect(Collectors.toSet());
        if (branchIds.isEmpty()) {
            return Map.of();
        }
        return branchRepository.findAllById(branchIds).stream()
                .collect(Collectors.toMap(Branch::getId, Function.identity()));
    }

    private ZoneId branchZone(UUID branchId) {
        Branch branch = branchRepository.findById(branchId).orElse(null);
        String tz = branch != null ? branch.getTimezone() : null;
        if (!StringUtils.hasText(tz)) {
            return ZoneId.of(DEFAULT_BRANCH_TIMEZONE);
        }
        try {
            return ZoneId.of(tz);
        } catch (RuntimeException e) {
            return ZoneId.of(DEFAULT_BRANCH_TIMEZONE);
        }
    }

    private UUID resolveScopeBranch(UUID requestedBranchId) {
        try {
            return dataScopeHelper.resolveEffectiveBranchId(requestedBranchId);
        } catch (BaseException e) {
            throw new BaseException(ErrorCode.FIN_403_OUT_OF_SCOPE);
        }
    }

    private void enforceScope(UUID branchId) {
        try {
            dataScopeHelper.enforceBranchAccess(branchId);
        } catch (BaseException e) {
            throw new BaseException(ErrorCode.FIN_403_OUT_OF_SCOPE);
        }
    }
}