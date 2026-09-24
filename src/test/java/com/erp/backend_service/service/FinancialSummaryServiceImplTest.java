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
import com.erp.core.dto.request.fin.RecalculateFinancialSummaryRequest;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.fin.FinancialSummaryResponse;
import com.erp.core.dto.response.fin.FinancialSummarySourceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test {@link FinancialSummaryServiceImpl} — nghiệp vụ S5-04 (list/detail/sources/recalculate/finalize).
 */
@ExtendWith(MockitoExtension.class)
class FinancialSummaryServiceImplTest {

    @Mock
    private BranchDailyFinancialSummaryRepository summaryRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private DataScopeHelper dataScopeHelper;

    @Mock
    private FinancialSummaryCalculationService calculationService;

    private FinancialSummaryService service;
    private FinancialSummaryMapper mapper;
    private UUID branchId;
    private LocalDate businessDate;

    @BeforeEach
    void setUp() {
        mapper = new FinancialSummaryMapper();
        service = new FinancialSummaryServiceImpl(
                summaryRepository, branchRepository, orderRepository, refundRepository, expenseRepository,
                mapper, dataScopeHelper, calculationService);
        branchId = UUID.randomUUID();
        businessDate = LocalDate.of(2026, 9, 23);
    }

    private Branch branchOf(UUID id) {
        Branch branch = new Branch();
        branch.setId(id);
        branch.setName("Chi nhánh 1");
        branch.setTimezone("Asia/Ho_Chi_Minh");
        return branch;
    }

    private BranchDailyFinancialSummary fullSummary(UUID id, String status) {
        BranchDailyFinancialSummary s = new BranchDailyFinancialSummary();
        s.setId(id);
        s.setBranchId(branchId);
        s.setBusinessDate(businessDate);
        s.setGrossRevenue(new BigDecimal("1000.00"));
        s.setDiscountAmount(new BigDecimal("50.00"));
        s.setNetRevenue(new BigDecimal("950.00"));
        s.setTotalCogs(new BigDecimal("600.00"));
        s.setGrossProfit(new BigDecimal("350.00"));
        s.setTotalExpense(new BigDecimal("100.00"));
        s.setNetProfit(new BigDecimal("250.00"));
        s.setOrderCount(2);
        s.setStatus(status);
        s.setCreatedAt(java.time.Instant.parse("2026-09-23T02:00:00Z"));
        s.setUpdatedAt(java.time.Instant.parse("2026-09-23T02:00:00Z"));
        return s;
    }

    private FinancialSummaryData matchingData(BranchDailyFinancialSummary s) {
        return new FinancialSummaryData(
                s.getGrossRevenue(), s.getDiscountAmount(),
                s.getNetRevenue().subtract(s.getGrossRevenue()).add(s.getDiscountAmount()).negate(),
                s.getNetRevenue(), s.getTotalCogs(), s.getGrossProfit(),
                s.getTotalExpense(), s.getNetProfit(), s.getOrderCount());
    }

    // ── list ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("List: fromDate > toDate → FIN_400_INVALID_PERIOD")
    void testListInvalidPeriodThrows() {
        BaseException ex = assertThrows(BaseException.class,
                () -> service.list(branchId, businessDate, businessDate.minusDays(1), null, 0, 10, "businessDate", "desc"));
        assertEquals(ErrorCode.FIN_400_INVALID_PERIOD, ex.getErrorCode());
    }

    @Test
    @DisplayName("List: status không hợp lệ → INVALID_REQUEST")
    void testListInvalidStatusThrows() {
        BaseException ex = assertThrows(BaseException.class,
                () -> service.list(null, null, null, "CLOSED", 0, 10, "businessDate", "desc"));
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("List: chi nhánh không tồn tại → FIN_404_BRANCH_NOT_FOUND")
    void testListBranchNotFoundThrows() {
        when(branchRepository.existsById(branchId)).thenReturn(false);

        BaseException ex = assertThrows(BaseException.class,
                () -> service.list(branchId, null, null, null, 0, 10, "businessDate", "desc"));
        assertEquals(ErrorCode.FIN_404_BRANCH_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("List thành công: trả về PageResponse với dữ liệu chi nhánh")
    void testListSuccess() {
        BranchDailyFinancialSummary s = fullSummary(UUID.randomUUID(), "DRAFT");
        Page<BranchDailyFinancialSummary> page = new PageImpl<>(List.of(s), PageRequest.of(0, 10), 1);
        when(branchRepository.existsById(branchId)).thenReturn(true);
        when(dataScopeHelper.resolveEffectiveBranchId(branchId)).thenReturn(branchId);
        when(summaryRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(branchRepository.findAllById(any())).thenReturn(List.of(branchOf(branchId)));

        PageResponse<FinancialSummaryResponse> result =
                service.list(branchId, null, null, "DRAFT", 0, 12, "businessDate", "desc");

        assertEquals(1, result.totalElements());
        FinancialSummaryResponse r = result.content().get(0);
        assertEquals("Chi nhánh 1", r.branchName());
        assertEquals("DRAFT", r.status());
        verify(dataScopeHelper).resolveEffectiveBranchId(branchId);
    }

    // ── get ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Get: summary không tồn tại → FIN_404_FINANCIAL_SUMMARY_NOT_FOUND")
    void testGetNotFoundThrows() {
        when(summaryRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        BaseException ex = assertThrows(BaseException.class, () -> service.get(UUID.randomUUID()));
        assertEquals(ErrorCode.FIN_404_FINANCIAL_SUMMARY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Get: ngoài phạm vi chi nhánh → FIN_403_OUT_OF_SCOPE")
    void testGetOutOfScopeThrows() {
        BranchDailyFinancialSummary s = fullSummary(UUID.randomUUID(), "DRAFT");
        when(summaryRepository.findById(any(UUID.class))).thenReturn(Optional.of(s));
        doThrow(new BaseException(ErrorCode.CROSS_SCOPE_DENIED))
                .when(dataScopeHelper).enforceBranchAccess(branchId);

        BaseException ex = assertThrows(BaseException.class, () -> service.get(s.getId()));
        assertEquals(ErrorCode.FIN_403_OUT_OF_SCOPE, ex.getErrorCode());
    }

    @Test
    @DisplayName("Get thành công: trả chi tiết kèm tên chi nhánh")
    void testGetSuccess() {
        BranchDailyFinancialSummary s = fullSummary(UUID.randomUUID(), "DRAFT");
        when(summaryRepository.findById(any(UUID.class))).thenReturn(Optional.of(s));
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branchOf(branchId)));

        FinancialSummaryResponse r = service.get(s.getId());

        assertEquals(s.getId().toString(), r.id());
        assertEquals("Chi nhánh 1", r.branchName());
        assertEquals(new BigDecimal("250.00"), r.netProfit());
    }

    // ── sources ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Sources: source không hợp lệ → INVALID_REQUEST")
    void testGetSourcesInvalidSourceThrows() {
        BranchDailyFinancialSummary s = fullSummary(UUID.randomUUID(), "DRAFT");
        when(summaryRepository.findById(any(UUID.class))).thenReturn(Optional.of(s));

        BaseException ex = assertThrows(BaseException.class,
                () -> service.getSources(s.getId(), "INVENTORY", 0, 10));
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("Sources ORDERS thành công: trả dữ liệu nguồn + phân trang")
    void testGetSourcesOrdersSuccess() {
        BranchDailyFinancialSummary s = fullSummary(UUID.randomUUID(), "DRAFT");
        when(summaryRepository.findById(any(UUID.class))).thenReturn(Optional.of(s));
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branchOf(branchId)));
        when(orderRepository.findCompletedByBranchBetween(any(UUID.class), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        FinancialSummarySourceResponse<?> result = service.getSources(s.getId(), "ORDERS", 0, 10);

        assertEquals("ORDERS", result.source());
        assertEquals(0, result.totalElements());
        verify(dataScopeHelper).enforceBranchAccess(branchId);
    }

    // ── recalculate ──────────────────────────────────────────────────

    @Test
    @DisplayName("Recalculate: chi nhánh không tồn tại → FIN_404_BRANCH_NOT_FOUND")
    void testRecalculateBranchNotFoundThrows() {
        RecalculateFinancialSummaryRequest req = new RecalculateFinancialSummaryRequest(branchId, businessDate);
        when(branchRepository.existsById(branchId)).thenReturn(false);

        BaseException ex = assertThrows(BaseException.class, () -> service.recalculate(req));
        assertEquals(ErrorCode.FIN_404_BRANCH_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Recalculate: ngoài phạm vi chi nhánh → FIN_403_OUT_OF_SCOPE")
    void testRecalculateOutOfScopeThrows() {
        RecalculateFinancialSummaryRequest req = new RecalculateFinancialSummaryRequest(branchId, businessDate);
        when(branchRepository.existsById(branchId)).thenReturn(true);
        doThrow(new BaseException(ErrorCode.CROSS_SCOPE_DENIED))
                .when(dataScopeHelper).enforceBranchAccess(branchId);

        BaseException ex = assertThrows(BaseException.class, () -> service.recalculate(req));
        assertEquals(ErrorCode.FIN_403_OUT_OF_SCOPE, ex.getErrorCode());
    }

    @Test
    @DisplayName("Recalculate thành công: uỷ quyền cho CalculationService và trả response")
    void testRecalculateSuccess() {
        BranchDailyFinancialSummary s = fullSummary(UUID.randomUUID(), "DRAFT");
        RecalculateFinancialSummaryRequest req = new RecalculateFinancialSummaryRequest(branchId, businessDate);
        when(branchRepository.existsById(branchId)).thenReturn(true);
        when(calculationService.recalculate(branchId, businessDate)).thenReturn(s);
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branchOf(branchId)));

        FinancialSummaryResponse r = service.recalculate(req);

        assertEquals("DRAFT", r.status());
        verify(calculationService).recalculate(branchId, businessDate);
    }

    // ── finalize ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Finalize: summary đã FINALIZED → FIN_409_SUMMARY_ALREADY_FINALIZED")
    void testFinalizeAlreadyFinalizedThrows() {
        BranchDailyFinancialSummary s = fullSummary(UUID.randomUUID(), "FINALIZED");
        when(summaryRepository.findById(any(UUID.class))).thenReturn(Optional.of(s));

        BaseException ex = assertThrows(BaseException.class, () -> service.finalize(s.getId()));
        assertEquals(ErrorCode.FIN_409_SUMMARY_ALREADY_FINALIZED, ex.getErrorCode());
        verify(summaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Finalize: summary không phải DRAFT → INVALID_REQUEST")
    void testFinalizeNonDraftThrows() {
        BranchDailyFinancialSummary s = fullSummary(UUID.randomUUID(), "PENDING");
        when(summaryRepository.findById(any(UUID.class))).thenReturn(Optional.of(s));

        BaseException ex = assertThrows(BaseException.class, () -> service.finalize(s.getId()));
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("Finalize: dữ liệu lệch khi đối soát → FIN_409_RECONCILIATION_MISMATCH")
    void testFinalizeMismatchThrows() {
        BranchDailyFinancialSummary s = fullSummary(UUID.randomUUID(), "DRAFT");
        when(summaryRepository.findById(any(UUID.class))).thenReturn(Optional.of(s));
        FinancialSummaryData data = matchingData(s);
        when(calculationService.compute(branchId, businessDate))
                .thenReturn(new FinancialSummaryData(
                        data.grossRevenue(), data.discountAmount(), data.processedRefund(),
                        data.netRevenue(), data.totalCogs(), data.grossProfit(),
                        data.totalExpense(), data.netProfit().add(BigDecimal.TEN), data.orderCount()));

        BaseException ex = assertThrows(BaseException.class, () -> service.finalize(s.getId()));
        assertEquals(ErrorCode.FIN_409_RECONCILIATION_MISMATCH, ex.getErrorCode());
        verify(summaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Finalize thành công: DRAFT → FINALIZED, số liệu khớp")
    void testFinalizeSuccess() {
        BranchDailyFinancialSummary s = fullSummary(UUID.randomUUID(), "DRAFT");
        when(summaryRepository.findById(any(UUID.class))).thenReturn(Optional.of(s));
        when(calculationService.compute(branchId, businessDate)).thenReturn(matchingData(s));
        when(summaryRepository.save(any(BranchDailyFinancialSummary.class))).thenAnswer(inv -> inv.getArgument(0));
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branchOf(branchId)));

        FinancialSummaryResponse r = service.finalize(s.getId());

        assertEquals("FINALIZED", r.status());
        assertEquals("FINALIZED", s.getStatus());
        verify(summaryRepository).save(s);
    }
}