package com.erp.backend_service.service.impl;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.ExpenseMapper;
import com.erp.backend_service.repository.BranchDailyFinancialSummaryRepository;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.ExpenseRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.ExpenseService;
import com.erp.backend_service.service.FinancialSummaryCalculationService;
import com.erp.core.domain.Branch;
import com.erp.core.domain.BranchDailyFinancialSummary;
import com.erp.core.domain.Expense;
import com.erp.core.dto.request.fin.CreateExpenseRequest;
import com.erp.core.dto.request.fin.UpdateExpenseRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.fin.ExpenseResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Triển khai {@link ExpenseService}: quản lý chi phí vận hành.
 * Chi phí trung tâm (branchId = null) không được tính vào branch_daily_financial_summary
 * cho đến khi BA định nghĩa cơ chế phân bổ.
 */
@Service
public class ExpenseServiceImpl implements ExpenseService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final String STATUS_FINALIZED = "FINALIZED";

    private static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> SORTABLE_COLUMNS = Set.of("expenseDate", "amount", "createdAt");

    private final ExpenseRepository expenseRepository;
    private final BranchDailyFinancialSummaryRepository summaryRepository;
    private final BranchRepository branchRepository;
    private final ExpenseMapper expenseMapper;
    private final DataScopeHelper dataScopeHelper;
    private final FinancialSummaryCalculationService calculationService;

    public ExpenseServiceImpl(ExpenseRepository expenseRepository,
                              BranchDailyFinancialSummaryRepository summaryRepository,
                              BranchRepository branchRepository,
                              ExpenseMapper expenseMapper,
                              DataScopeHelper dataScopeHelper,
                              FinancialSummaryCalculationService calculationService) {
        this.expenseRepository = expenseRepository;
        this.summaryRepository = summaryRepository;
        this.branchRepository = branchRepository;
        this.expenseMapper = expenseMapper;
        this.dataScopeHelper = dataScopeHelper;
        this.calculationService = calculationService;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> list(int page, int size, String search, UUID branchId,
                                              String category, String status,
                                              LocalDate dateFrom, LocalDate dateTo,
                                              String sortBy, String sortDir) {
        int pageIdx = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        UUID effectiveBranchId = dataScopeHelper.resolveEffectiveBranchId(branchId);
        String cleanSearch = StringUtils.hasText(search) ? search.trim() : null;
        String cleanCategory = StringUtils.hasText(category) ? category.trim() : null;
        String cleanStatus = StringUtils.hasText(status) ? status.trim() : null;

        String sortProperty = SORTABLE_COLUMNS.contains(sortBy) ? sortBy : "expenseDate";
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(sortProperty).ascending() : Sort.by(sortProperty).descending();
        Pageable pageable = PageRequest.of(pageIdx, pageSize, sort);

        Specification<Expense> spec = (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (effectiveBranchId != null) {
                predicates = cb.and(predicates, cb.equal(root.get("branchId"), effectiveBranchId));
            }
            if (cleanCategory != null) {
                predicates = cb.and(predicates, cb.equal(root.get("expenseCategory"), cleanCategory));
            }
            if (cleanStatus != null) {
                predicates = cb.and(predicates, cb.equal(root.get("status"), cleanStatus));
            }
            if (dateFrom != null) {
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("expenseDate"), dateFrom));
            }
            if (dateTo != null) {
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(root.get("expenseDate"), dateTo));
            }
            if (cleanSearch != null) {
                String like = "%" + cleanSearch.toLowerCase() + "%";
                predicates = cb.and(predicates, cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("expenseCategory"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), like)));
            }
            return predicates;
        };

        Page<Expense> expensePage = expenseRepository.findAll(spec, pageable);
        List<ExpenseResponse> content = expensePage.getContent().stream()
                .map(this::toResponse)
                .toList();

        return new PageResponse<>(
                expensePage.getNumber(),
                expensePage.getSize(),
                expensePage.getTotalElements(),
                expensePage.getTotalPages(),
                content
        );
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public ExpenseResponse get(UUID id) {
        Expense expense = findRequired(id);
        enforceScope(expense.getBranchId());
        return toResponse(expense);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ExpenseResponse create(CreateExpenseRequest request) {
        UUID branchId = request.branchId();
        validateBranchAccess(branchId);
        assertNotFinalized(branchId, request.expenseDate());

        Expense expense = new Expense();
        expense.setBranchId(branchId);
        expense.setExpenseDate(request.expenseDate());
        expense.setExpenseCategory(request.expenseCategory().trim());
        expense.setAmount(request.amount());
        expense.setDescription(request.description());
        expense.setStatus(STATUS_ACTIVE);

        Expense saved = expenseRepository.save(expense);
        recalculateSummary(branchId, request.expenseDate());
        return toResponse(saved);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ExpenseResponse update(UUID id, UpdateExpenseRequest request) {
        Expense expense = findRequired(id);
        enforceScope(expense.getBranchId());

        UUID oldBranchId = expense.getBranchId();
        LocalDate oldExpenseDate = expense.getExpenseDate();

        UUID newBranchId = request.branchId();
        validateBranchAccess(newBranchId);

        assertNotFinalized(oldBranchId, oldExpenseDate);
        assertNotFinalized(newBranchId, request.expenseDate());

        expense.setBranchId(newBranchId);
        expense.setExpenseDate(request.expenseDate());
        expense.setExpenseCategory(request.expenseCategory().trim());
        expense.setAmount(request.amount());
        expense.setDescription(request.description());

        Expense saved = expenseRepository.save(expense);

        boolean sameTarget = Objects.equals(oldBranchId, newBranchId)
                && oldExpenseDate.isEqual(request.expenseDate());
        if (oldBranchId != null) {
            recalculateSummary(oldBranchId, oldExpenseDate);
        }
        if (newBranchId != null && !sameTarget) {
            recalculateSummary(newBranchId, request.expenseDate());
        }
        return toResponse(saved);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(UUID id) {
        Expense expense = findRequired(id);
        enforceScope(expense.getBranchId());
        assertNotFinalized(expense.getBranchId(), expense.getExpenseDate());

        if (STATUS_INACTIVE.equals(expense.getStatus())) {
            return;
        }
        expense.setStatus(STATUS_INACTIVE);
        expenseRepository.save(expense);
        recalculateSummary(expense.getBranchId(), expense.getExpenseDate());
    }

    // ── Private helpers ───────────────────────────────────────────────

    private Expense findRequired(UUID id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new BaseException(ErrorCode.FIN_404_EXPENSE_NOT_FOUND));
    }

    /**
     * Kiểm tra tồn tại chi nhánh và phạm vi truy cập. Chi phí trung tâm (branchId = null) bỏ qua.
     */
    private void validateBranchAccess(UUID branchId) {
        if (branchId == null) {
            return;
        }
        if (!branchRepository.existsById(branchId)) {
            throw new BaseException(ErrorCode.FIN_404_EXPENSE_BRANCH_NOT_FOUND);
        }
        dataScopeHelper.enforceBranchAccess(branchId);
    }

    /**
     * Chặn truy cập dữ liệu chi phí của chi nhánh ngoài phạm vi. Chi phí trung tâm bỏ qua.
     */
    private void enforceScope(UUID branchId) {
        if (branchId != null) {
            dataScopeHelper.enforceBranchAccess(branchId);
        }
    }

    /**
     * Chặn ghi nhận/sửa/xóa khi kỳ tài chính của chi nhánh trong ngày đã chốt (FINALIZED).
     * Chi phí trung tâm không nằm trong summary nên không bị chặn.
     */
    private void assertNotFinalized(UUID branchId, LocalDate businessDate) {
        if (branchId == null) {
            return;
        }
        summaryRepository.findByBranchIdAndBusinessDate(branchId, businessDate)
                .filter(s -> STATUS_FINALIZED.equals(s.getStatus()))
                .ifPresent(s -> {
                    throw new BaseException(ErrorCode.FIN_400_EXPENSE_FINALIZED_PERIOD);
                });
    }

    /**
     * Tái tính total_expense của summary chi nhánh/tự động, cập nhật net_profit = gross_profit - total_expense.
     * - Chi phí trung tâm (branchId = null) bị loại khỏi summary.
     * - Không tính lại summary đã FINALIZED.
     * - Nếu summary chưa tồn tại cho ngày đó, tạo mới trạng thái DRAFT để chi phí phản ánh vào báo cáo.
     */
    private void recalculateSummary(UUID branchId, LocalDate businessDate) {
        if (branchId == null) {
            return;
        }
        BigDecimal totalExpense = calculationService.calculateTotalExpense(branchId, businessDate);

        BranchDailyFinancialSummary summary = summaryRepository
                .findByBranchIdAndBusinessDate(branchId, businessDate)
                .orElseGet(() -> {
                    BranchDailyFinancialSummary created = new BranchDailyFinancialSummary();
                    created.setBranchId(branchId);
                    created.setBusinessDate(businessDate);
                    created.setStatus("DRAFT");
                    return created;
                });

        if (STATUS_FINALIZED.equals(summary.getStatus())) {
            return;
        }

        BigDecimal grossProfit = summary.getGrossProfit() != null ? summary.getGrossProfit() : BigDecimal.ZERO;
        summary.setTotalExpense(totalExpense);
        summary.setNetProfit(grossProfit.subtract(totalExpense));
        summaryRepository.save(summary);
    }

    private ExpenseResponse toResponse(Expense expense) {
        Branch branch = expense.getBranchId() != null
                ? branchRepository.findById(expense.getBranchId()).orElse(null)
                : null;
        return expenseMapper.toResponse(expense, branch);
    }
}