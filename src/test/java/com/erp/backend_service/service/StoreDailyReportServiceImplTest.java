package com.erp.backend_service.service;

import com.erp.backend_service.exception.BaseException;
import com.erp.backend_service.exception.ErrorCode;
import com.erp.backend_service.mapper.StoreDailyReportMapper;
import com.erp.backend_service.repository.AccountRepository;
import com.erp.backend_service.repository.BranchRepository;
import com.erp.backend_service.repository.ShiftAssignmentRepository;
import com.erp.backend_service.repository.ShiftReportRepository;
import com.erp.backend_service.repository.StoreDailyReportRepository;
import com.erp.backend_service.security.DataScopeHelper;
import com.erp.backend_service.service.impl.StoreDailyReportServiceImpl;
import com.erp.core.domain.Account;
import com.erp.core.domain.ShiftReport;
import com.erp.core.domain.StoreDailyReport;
import com.erp.core.dto.request.store.CreateDailyReportRequest;
import com.erp.core.dto.response.store.StoreDailyReportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreDailyReportServiceImplTest {

    @Mock
    private StoreDailyReportRepository storeDailyReportRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private ShiftReportRepository shiftReportRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private DataScopeHelper dataScopeHelper;

    private StoreDailyReportMapper storeDailyReportMapper;
    private StoreDailyReportServiceImpl storeDailyReportService;

    private UUID branchId;
    private UUID accountId;
    private LocalDate reportDate;

    @BeforeEach
    void setUp() {
        storeDailyReportMapper = new StoreDailyReportMapper();
        storeDailyReportService = new StoreDailyReportServiceImpl(
                storeDailyReportRepository,
                shiftAssignmentRepository,
                shiftReportRepository,
                branchRepository,
                accountRepository,
                storeDailyReportMapper,
                dataScopeHelper
        );

        branchId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        reportDate = LocalDate.now();
    }

    @Test
    @DisplayName("Chặn chốt sổ ngày theo BR-STORE-07: Còn ca đang CHECKED_IN hoặc SCHEDULED")
    void testGenerateDailyReport_Fail_ActiveShiftsRemaining() {
        when(branchRepository.existsById(branchId)).thenReturn(true);
        // Có 1 ca đang hoạt động
        when(shiftAssignmentRepository.countByBranchIdAndWorkDateAndStatusIn(eq(branchId), eq(reportDate), any()))
                .thenReturn(1L);

        CreateDailyReportRequest request = new CreateDailyReportRequest(branchId, reportDate, BigDecimal.ZERO, "Chốt ngày");

        BaseException ex = assertThrows(BaseException.class,
                () -> storeDailyReportService.generateDailyReport(request, accountId));
        assertEquals(ErrorCode.STORE_400_ACTIVE_SHIFTS_REMAINING, ex.getErrorCode());
    }

    @Test
    @DisplayName("Chốt sổ ngày thành công: Tổng hợp chính xác doanh thu từ tất cả các ca trong ngày")
    void testGenerateDailyReport_Success() {
        when(branchRepository.existsById(branchId)).thenReturn(true);
        when(shiftAssignmentRepository.countByBranchIdAndWorkDateAndStatusIn(eq(branchId), eq(reportDate), any()))
                .thenReturn(0L);

        ShiftReport shift1 = new ShiftReport();
        shift1.setOrdersCount(10);
        shift1.setTotalSales(new BigDecimal("2000000"));
        shift1.setCashSales(new BigDecimal("1000000"));
        shift1.setBankTransferSales(new BigDecimal("1000000"));

        ShiftReport shift2 = new ShiftReport();
        shift2.setOrdersCount(15);
        shift2.setTotalSales(new BigDecimal("3500000"));
        shift2.setCashSales(new BigDecimal("1500000"));
        shift2.setBankTransferSales(new BigDecimal("2000000"));

        when(shiftReportRepository.findByBranchIdAndBusinessDate(branchId, reportDate))
                .thenReturn(List.of(shift1, shift2));

        when(storeDailyReportRepository.findByBranchIdAndBusinessDate(branchId, reportDate))
                .thenReturn(Optional.empty());
        when(storeDailyReportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Account account = new Account();
        account.setFullName("Cửa Hàng Trưởng");
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        CreateDailyReportRequest request = new CreateDailyReportRequest(branchId, reportDate, new BigDecimal("500000"), "Chốt ngày");
        StoreDailyReportResponse response = storeDailyReportService.generateDailyReport(request, accountId);

        assertNotNull(response);
        assertEquals(25, response.totalOrders());
        assertEquals(new BigDecimal("5500000"), response.grossRevenue());
        assertEquals(new BigDecimal("2500000"), response.cashAmount());
        assertEquals(new BigDecimal("3000000"), response.transferAmount());
        // Closing Cash = opening (500k) + cashAmount (2.5M) = 3M
        assertEquals(new BigDecimal("3000000"), response.closingCash());
    }

    @Test
    @DisplayName("Phê duyệt báo cáo ngày: Chuyển trạng thái sang RECONCILED")
    void testApproveDailyReport_Success() {
        UUID reportId = UUID.randomUUID();
        StoreDailyReport report = new StoreDailyReport();
        report.setBranchId(branchId);
        report.setStatus("OPEN");
        report.setSubmittedById(accountId);

        when(storeDailyReportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(storeDailyReportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Account approver = new Account();
        approver.setFullName("Kế toán trưởng");
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(approver));

        StoreDailyReportResponse response = storeDailyReportService.approveDailyReport(reportId, accountId, "Đã khớp quỹ");
        assertNotNull(response);
        assertEquals("RECONCILED", response.status());
    }
}
